"""OAuth 2.1 (DCR + PKCE) pour le connecteur Claude, adossé à Supabase Auth.

Parcours :
1. Claude s'enregistre (Dynamic Client Registration) puis ouvre /authorize (PKCE obligatoire, géré par le SDK).
2. ``authorize`` redirige vers /login : l'utilisateur saisit SON e-mail/mot de passe Supabase.
3. Le serveur obtient une session Supabase (grant password) et la SCELLE (chiffrée + authentifiée)
   dans le code d'autorisation renvoyé à Claude.
4. Claude échange le code contre un jeton d'accès scellé (≤ 1 h) + un refresh token scellé (rotatif :
   chaque rafraîchissement consomme le refresh token Supabase, un rejeu échoue en invalid_grant).
5. À chaque appel d'outil : jeton → utilisateur + JWT Supabase de CET utilisateur → RLS côté base.

Sans état côté serveur (aucune base OAuth à héberger) ; aucune clé service_role ;
seuls les e-mails de COACH_ALLOWED_EMAILS peuvent se connecter ; retours limités aux URL de Claude.
"""

from __future__ import annotations

import base64
import hashlib
import html
import json
import secrets
import re
import time
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlencode

from cryptography.fernet import Fernet, InvalidToken
from mcp.server.auth.middleware.auth_context import get_access_token
from mcp.server.auth.provider import (
    AccessToken,
    AuthorizationCode,
    AuthorizationParams,
    OAuthAuthorizationServerProvider,
    RefreshToken,
    RegistrationError,
    TokenError,
    construct_redirect_uri,
)
from mcp.shared.auth import OAuthClientInformationFull, OAuthToken
from starlette.requests import Request
from starlette.responses import HTMLResponse, RedirectResponse, Response

from .backend import Identity

try:
    import httpx2 as httpx
except ImportError:  # pragma: no cover
    import httpx  # type: ignore[no-redef]

ACCESS_TTL = 3600
REFRESH_TTL = 30 * 24 * 3600
CODE_TTL = 300
PENDING_TTL = 600
SCOPE = "coach"


def _h(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


# ---------------------------------------------------------------- Supabase Auth
@dataclass
class SupabaseSession:
    user_id: str
    email: str | None
    access_token: str
    refresh_token: str
    expires_at: int


class AuthFailed(Exception):
    pass


class SupabaseAuthClient(Protocol):
    async def password_login(self, email: str, password: str) -> SupabaseSession: ...
    async def refresh(self, refresh_token: str) -> SupabaseSession: ...


class HttpSupabaseAuth:
    def __init__(self, url: str, anon_key: str):
        self._url = url.rstrip("/") + "/auth/v1/token"
        self._key = anon_key
        self._client = httpx.AsyncClient(timeout=15)

    async def _token(self, grant: str, body: dict[str, str]) -> SupabaseSession:
        r = await self._client.post(f"{self._url}?grant_type={grant}", json=body,
                                    headers={"apikey": self._key, "Content-Type": "application/json"})
        if r.status_code != 200:
            raise AuthFailed(r.status_code)
        d = r.json()
        return SupabaseSession(d["user"]["id"], d["user"].get("email"), d["access_token"], d["refresh_token"],
                               int(d.get("expires_at") or time.time() + int(d.get("expires_in", 3600))))

    async def password_login(self, email: str, password: str) -> SupabaseSession:
        return await self._token("password", {"email": email, "password": password})

    async def refresh(self, refresh_token: str) -> SupabaseSession:
        return await self._token("refresh_token", {"refresh_token": refresh_token})


# ---------------------------------------------------------------- jetons scellés (sans état serveur)
class Sealer:
    """Chiffre + authentifie (Fernet : AES-128-CBC + HMAC-SHA256, horodaté) de petits objets JSON.

    Aucun état à conserver côté serveur : client DCR, demande en cours, code, jetons d'accès et de
    rafraîchissement sont des blobs scellés. Le serveur peut redémarrer (hébergeur gratuit qui s'endort)
    sans déconnecter Claude. Seule la clé COACH_TOKEN_ENC_KEY doit rester stable et secrète.
    """

    def __init__(self, secret: str):
        if len(secret) < 32:
            raise ValueError("COACH_TOKEN_ENC_KEY : au moins 32 caractères aléatoires")
        # clé Fernet dérivée : accepte n'importe quel secret long (ex. valeur générée par l'hébergeur)
        self._f = Fernet(base64.urlsafe_b64encode(hashlib.sha256(secret.encode()).digest()))

    def seal(self, kind: str, data: dict[str, Any]) -> str:
        return kind + "." + self._f.encrypt(json.dumps({"k": kind, **data}).encode()).decode()

    def open(self, kind: str, token: str, ttl: int | None = None) -> dict[str, Any] | None:
        if not token or not token.startswith(kind + "."):
            return None
        try:
            d = json.loads(self._f.decrypt(token[len(kind) + 1:].encode(), ttl=ttl))
        except (InvalidToken, ValueError):
            return None
        return d if d.get("k") == kind else None


ALLOWED_REDIRECTS = re.compile(
    r"^(https://claude\.ai/api/mcp/auth_callback|https://claude\.com/api/mcp/auth_callback"
    r"|http://(localhost|127\.0\.0\.1)(:\d+)?/callback)$")


# ---------------------------------------------------------------- provider OAuth
class CoachOAuthProvider(OAuthAuthorizationServerProvider[AuthorizationCode, RefreshToken, AccessToken]):
    def __init__(self, sealer: Sealer, supabase: SupabaseAuthClient, base_url: str, allowed_emails: set[str]):
        self.sealer = sealer
        self.supabase = supabase
        self.base_url = base_url.rstrip("/")
        self.resource = self.base_url + "/mcp"
        self.allowed = {e.strip().lower() for e in allowed_emails if e.strip()}
        if not self.allowed:
            raise ValueError("COACH_ALLOWED_EMAILS est obligatoire")
        self._failures: dict[str, int] = {}
        self._used_codes: dict[str, float] = {}  # usage unique des codes (mémoire, durée de vie 5 min)

    # -- clients (DCR sans état : l'identifiant client EST l'enregistrement scellé)
    async def register_client(self, client_info: OAuthClientInformationFull) -> None:
        uris = [str(u) for u in (client_info.redirect_uris or [])]
        if not uris or not all(ALLOWED_REDIRECTS.match(u) for u in uris):
            raise RegistrationError("invalid_redirect_uri", "seuls les retours vers Claude sont acceptés")
        client_info.client_id = self.sealer.seal("cl", {"info": client_info.model_dump(mode="json", exclude={"client_id"})})

    async def get_client(self, client_id: str) -> OAuthClientInformationFull | None:
        d = self.sealer.open("cl", client_id)
        return OAuthClientInformationFull.model_validate({**d["info"], "client_id": client_id}) if d else None

    # -- /authorize → page de connexion
    async def authorize(self, client: OAuthClientInformationFull, params: AuthorizationParams) -> str:
        req = self.sealer.seal("rq", {"c": client.client_id, "p": params.model_dump(mode="json")})
        return f"{self.base_url}/login?{urlencode({'req': req})}"

    async def login_page(self, request: Request) -> Response:
        form = await request.form() if request.method == "POST" else {}
        req = request.query_params.get("req") or str(form.get("req", ""))
        d = self.sealer.open("rq", req, ttl=PENDING_TTL)
        if not d:
            return HTMLResponse(_page("Demande expirée", "<p>Relancez la connexion depuis Claude.</p>"), status_code=400)
        client_id = d["c"]
        params = AuthorizationParams.model_validate(d["p"])
        client = await self.get_client(client_id)
        key = hashlib.sha256(req.encode()).hexdigest()
        error = ""
        if request.method == "POST":
            email = str(form.get("email", "")).strip().lower()
            if self._failures.get(key, 0) >= 5:
                return HTMLResponse(_page("Trop d'essais", "<p>Relancez la connexion depuis Claude.</p>"), status_code=429)
            try:
                if email not in self.allowed:
                    raise AuthFailed("not_allowed")
                sess = await self.supabase.password_login(email, str(form.get("password", "")))
            except AuthFailed:
                self._failures[key] = self._failures.get(key, 0) + 1
                error = "<p class=err>Identifiants refusés.</p>"
            else:
                code = self.sealer.seal("co", {
                    "c": client_id, "s": params.scopes or [SCOPE], "ch": params.code_challenge,
                    "ru": str(params.redirect_uri), "rx": params.redirect_uri_provided_explicitly,
                    "r": params.resource or self.resource, "sub": sess.user_id,
                    "sb": {"a": sess.access_token, "r": sess.refresh_token, "e": sess.expires_at}})
                return RedirectResponse(construct_redirect_uri(str(params.redirect_uri), code=code, state=params.state),
                                        status_code=302)
        name = html.escape((client.client_name if client else None) or "Claude")
        body = f"""{error}<p><b>{name}</b> demande l'accès à vos données Coach Sportif
          (lecture des séances, publication des plannings validés).</p>
          <form method=post><input type=hidden name=req value="{html.escape(req)}">
          <label>E-mail<input name=email type=email autocomplete=username required></label>
          <label>Mot de passe<input name=password type=password autocomplete=current-password required></label>
          <button>Autoriser</button></form>"""
        return HTMLResponse(_page("Connexion Coach Sportif", body), headers={"X-Frame-Options": "DENY",
                            "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; "
                            "form-action 'self' https://claude.ai https://claude.com http://localhost:* http://127.0.0.1:*"})

    # -- codes & jetons
    async def load_authorization_code(self, client: OAuthClientInformationFull, authorization_code: str) -> AuthorizationCode | None:
        d = self.sealer.open("co", authorization_code, ttl=CODE_TTL)
        if not d or d["c"] != client.client_id or authorization_code in self._used_codes:
            return None
        return AuthorizationCode(code=authorization_code, scopes=d["s"], expires_at=time.time() + CODE_TTL, client_id=d["c"],
                                 code_challenge=d["ch"], redirect_uri=d["ru"], redirect_uri_provided_explicitly=d["rx"],
                                 resource=d["r"], subject=d["sub"])

    def _issue(self, client_id: str, sub: str, scopes: list[str], resource: str | None, sb: dict[str, Any]) -> OAuthToken:
        exp = min(int(sb["e"]), int(time.time()) + ACCESS_TTL)
        access = self.sealer.seal("at", {"c": client_id, "sub": sub, "s": scopes, "r": resource, "x": exp, "sba": sb["a"]})
        refresh = self.sealer.seal("rt", {"c": client_id, "sub": sub, "s": scopes, "r": resource, "sbr": sb["r"]})
        return OAuthToken(access_token=access, token_type="Bearer", expires_in=max(60, exp - int(time.time())),
                          refresh_token=refresh, scope=" ".join(scopes))

    async def exchange_authorization_code(self, client: OAuthClientInformationFull, authorization_code: AuthorizationCode) -> OAuthToken:
        now = time.time()
        self._used_codes = {c: t for c, t in self._used_codes.items() if t > now - CODE_TTL}
        if authorization_code.code in self._used_codes:
            raise TokenError("invalid_grant", "code déjà utilisé")
        self._used_codes[authorization_code.code] = now
        d = self.sealer.open("co", authorization_code.code, ttl=CODE_TTL)
        if not d:
            raise TokenError("invalid_grant", "code expiré")
        return self._issue(client.client_id, d["sub"], d["s"], d["r"], d["sb"])

    async def load_refresh_token(self, client: OAuthClientInformationFull, refresh_token: str) -> RefreshToken | None:
        d = self.sealer.open("rt", refresh_token, ttl=REFRESH_TTL)
        if not d or d["c"] != client.client_id:
            return None
        return RefreshToken(token=refresh_token, client_id=d["c"], scopes=d["s"], resource=d["r"], subject=d["sub"])

    async def exchange_refresh_token(self, client: OAuthClientInformationFull, refresh_token: RefreshToken, scopes: list[str]) -> OAuthToken:
        d = self.sealer.open("rt", refresh_token.token, ttl=REFRESH_TTL)
        try:  # Supabase invalide l'ancien refresh token : rotation réelle, un jeton rejoué échoue
            s = await self.supabase.refresh(d["sbr"]) if d else None
        except AuthFailed:
            s = None
        if s is None or s.user_id != d["sub"]:
            raise TokenError("invalid_grant", "session expirée ou révoquée")
        return self._issue(client.client_id, s.user_id, scopes or d["s"], d["r"],
                           {"a": s.access_token, "r": s.refresh_token, "e": s.expires_at})

    async def load_access_token(self, token: str) -> AccessToken | None:
        d = self.sealer.open("at", token, ttl=ACCESS_TTL + 60)
        if not d or d["x"] < time.time():
            return None
        return AccessToken(token=token, client_id=d["c"], scopes=d["s"], expires_at=d["x"], resource=d["r"],
                           subject=d["sub"], claims={"sba": d["sba"]})

    async def revoke_token(self, token: AccessToken | RefreshToken) -> None:
        return None  # jetons scellés : expiration courte ; révocation globale = changer COACH_TOKEN_ENC_KEY

    async def current_identity(self) -> Identity:
        at = get_access_token()
        if at is None or not at.subject or not at.claims:
            raise AuthFailed("unauthenticated")
        return Identity(user_id=at.subject, supabase_access_token=at.claims["sba"])


def _page(title: str, body: str) -> str:
    return f"""<!doctype html><html lang=fr><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title><style>
body{{font-family:system-ui,sans-serif;max-width:420px;margin:48px auto;padding:0 16px;color:#1b1f24;background:#f6f7f9}}
h1{{font-size:22px}} label{{display:block;margin:14px 0 4px;font-weight:600}} input{{width:100%;padding:12px;font-size:16px;
border:1px solid #c8cdd3;border-radius:10px;box-sizing:border-box}} button{{margin-top:20px;width:100%;padding:14px;font-size:16px;
background:#0b6e4f;color:#fff;border:0;border-radius:10px}} .err{{color:#b00020;font-weight:600}}</style>
<h1>🏃 {html.escape(title)}</h1>{body}</html>"""
