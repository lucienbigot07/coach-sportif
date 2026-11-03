"""OAuth 2.1 (DCR + PKCE) pour le connecteur Claude, adossé à Supabase Auth.

Parcours :
1. Claude s'enregistre (Dynamic Client Registration) puis ouvre /authorize (PKCE obligatoire, géré par le SDK).
2. ``authorize`` redirige vers /login : l'utilisateur saisit SON e-mail/mot de passe Supabase.
3. Le serveur obtient une session Supabase (grant password), conserve le refresh token CHIFFRÉ (Fernet),
   puis renvoie un code d'autorisation à Claude.
4. Claude échange le code contre un jeton MCP opaque (1 h) + refresh token (30 j, rotatif).
5. À chaque appel d'outil : jeton MCP → utilisateur → JWT Supabase de CET utilisateur → RLS côté base.

Aucune clé service_role. Seuls les e-mails de COACH_ALLOWED_EMAILS peuvent se connecter.
Les jetons sont stockés hachés (SHA-256) dans SQLite (volume persistant de l'hébergeur).
"""

from __future__ import annotations

import asyncio
import hashlib
import html
import json
import secrets
import sqlite3
import time
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlencode

from cryptography.fernet import Fernet
from mcp.server.auth.middleware.auth_context import get_access_token
from mcp.server.auth.provider import (
    AccessToken,
    AuthorizationCode,
    AuthorizationParams,
    AuthorizeError,
    OAuthAuthorizationServerProvider,
    RefreshToken,
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


# ---------------------------------------------------------------- stockage
class Store:
    def __init__(self, path: str, enc_key: str):
        self._db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._db.execute("pragma journal_mode=wal")
        self._f = Fernet(enc_key.encode() if isinstance(enc_key, str) else enc_key)
        self._db.executescript("""
          create table if not exists clients(client_id text primary key, info text not null);
          create table if not exists pending(req_id text primary key, client_id text, params text, created_at int);
          create table if not exists codes(code_hash text primary key, data text, expires_at int);
          create table if not exists tokens(hash text primary key, kind text, client_id text, user_id text,
                                            scopes text, resource text, expires_at int);
          create table if not exists supa(user_id text primary key, email text, enc text, expires_at int);
        """)

    def q(self, sql: str, *args: Any) -> list[tuple]:
        return self._db.execute(sql, args).fetchall()

    def enc(self, s: str) -> str:
        return self._f.encrypt(s.encode()).decode()

    def dec(self, s: str) -> str:
        return self._f.decrypt(s.encode()).decode()

    def purge(self) -> None:
        now = int(time.time())
        self.q("delete from pending where created_at < ?", now - PENDING_TTL)
        self.q("delete from codes where expires_at < ?", now)
        self.q("delete from tokens where expires_at < ?", now)


# ---------------------------------------------------------------- provider OAuth
class CoachOAuthProvider(OAuthAuthorizationServerProvider[AuthorizationCode, RefreshToken, AccessToken]):
    def __init__(self, store: Store, supabase: SupabaseAuthClient, base_url: str, allowed_emails: set[str]):
        self.store = store
        self.supabase = supabase
        self.base_url = base_url.rstrip("/")
        self.resource = self.base_url + "/mcp"
        self.allowed = {e.strip().lower() for e in allowed_emails if e.strip()}
        if not self.allowed:
            raise ValueError("COACH_ALLOWED_EMAILS est obligatoire")
        self._locks: dict[str, asyncio.Lock] = {}
        self._failures: dict[str, int] = {}

    # -- clients (DCR)
    async def get_client(self, client_id: str) -> OAuthClientInformationFull | None:
        rows = self.store.q("select info from clients where client_id = ?", client_id)
        return OAuthClientInformationFull.model_validate_json(rows[0][0]) if rows else None

    async def register_client(self, client_info: OAuthClientInformationFull) -> None:
        self.store.q("insert or replace into clients values (?, ?)", client_info.client_id, client_info.model_dump_json())

    # -- /authorize → page de connexion
    async def authorize(self, client: OAuthClientInformationFull, params: AuthorizationParams) -> str:
        self.store.purge()
        req_id = secrets.token_urlsafe(32)
        self.store.q("insert into pending values (?, ?, ?, ?)", req_id, client.client_id,
                     params.model_dump_json(), int(time.time()))
        return f"{self.base_url}/login?{urlencode({'req': req_id})}"

    async def login_page(self, request: Request) -> Response:
        req_id = request.query_params.get("req") or (await request.form()).get("req")
        rows = self.store.q("select client_id, params, created_at from pending where req_id = ?", req_id or "")
        if not rows or rows[0][2] < time.time() - PENDING_TTL:
            return HTMLResponse(_page("Demande expirée", "<p>Relancez la connexion depuis Claude.</p>"), status_code=400)
        client_id, params_json, _ = rows[0]
        params = AuthorizationParams.model_validate_json(params_json)
        client = await self.get_client(client_id)
        error = ""
        if request.method == "POST":
            form = await request.form()
            email = str(form.get("email", "")).strip().lower()
            if self._failures.get(req_id, 0) >= 5:
                return HTMLResponse(_page("Trop d'essais", "<p>Relancez la connexion depuis Claude.</p>"), status_code=429)
            try:
                if email not in self.allowed:
                    raise AuthFailed("not_allowed")
                sess = await self.supabase.password_login(email, str(form.get("password", "")))
            except AuthFailed:
                self._failures[req_id] = self._failures.get(req_id, 0) + 1
                error = "<p class=err>Identifiants refusés.</p>"
            else:
                self._save_supabase(sess)
                self.store.q("delete from pending where req_id = ?", req_id)
                code = secrets.token_urlsafe(32)
                ac = AuthorizationCode(code=code, scopes=params.scopes or [SCOPE], expires_at=time.time() + CODE_TTL,
                                       client_id=client_id, code_challenge=params.code_challenge,
                                       redirect_uri=params.redirect_uri,
                                       redirect_uri_provided_explicitly=params.redirect_uri_provided_explicitly,
                                       resource=params.resource or self.resource, subject=sess.user_id)
                self.store.q("insert into codes values (?, ?, ?)", _h(code), ac.model_dump_json(), int(ac.expires_at))
                return RedirectResponse(construct_redirect_uri(str(params.redirect_uri), code=code, state=params.state),
                                        status_code=302)
        name = html.escape((client.client_name if client else None) or "Claude")
        body = f"""{error}<p><b>{name}</b> demande l'accès à vos données Coach Sportif
          (lecture des séances, publication des plannings validés).</p>
          <form method=post><input type=hidden name=req value="{html.escape(req_id)}">
          <label>E-mail<input name=email type=email autocomplete=username required></label>
          <label>Mot de passe<input name=password type=password autocomplete=current-password required></label>
          <button>Autoriser</button></form>"""
        return HTMLResponse(_page("Connexion Coach Sportif", body), headers={"X-Frame-Options": "DENY",
                            "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'"})

    # -- codes & jetons
    async def load_authorization_code(self, client: OAuthClientInformationFull, authorization_code: str) -> AuthorizationCode | None:
        rows = self.store.q("select data, expires_at from codes where code_hash = ?", _h(authorization_code))
        if not rows or rows[0][1] < time.time():
            return None
        ac = AuthorizationCode.model_validate_json(rows[0][0])
        return ac if ac.client_id == client.client_id else None

    def _issue(self, client_id: str, user_id: str, scopes: list[str], resource: str | None) -> OAuthToken:
        access, refresh = secrets.token_urlsafe(40), secrets.token_urlsafe(48)
        now = int(time.time())
        self.store.q("insert into tokens values (?, 'access', ?, ?, ?, ?, ?)", _h(access), client_id, user_id,
                     " ".join(scopes), resource, now + ACCESS_TTL)
        self.store.q("insert into tokens values (?, 'refresh', ?, ?, ?, ?, ?)", _h(refresh), client_id, user_id,
                     " ".join(scopes), resource, now + REFRESH_TTL)
        return OAuthToken(access_token=access, token_type="Bearer", expires_in=ACCESS_TTL, refresh_token=refresh,
                          scope=" ".join(scopes))

    async def exchange_authorization_code(self, client: OAuthClientInformationFull, authorization_code: AuthorizationCode) -> OAuthToken:
        deleted = self.store.q("delete from codes where code_hash = ? returning code_hash", _h(authorization_code.code))
        if not deleted:  # usage unique
            raise TokenError("invalid_grant", "code déjà utilisé")
        return self._issue(client.client_id, authorization_code.subject or "", authorization_code.scopes,
                           authorization_code.resource or self.resource)

    async def load_refresh_token(self, client: OAuthClientInformationFull, refresh_token: str) -> RefreshToken | None:
        rows = self.store.q("select client_id, user_id, scopes, resource, expires_at from tokens where hash = ? and kind = 'refresh'",
                            _h(refresh_token))
        if not rows or rows[0][0] != client.client_id or rows[0][4] < time.time():
            return None
        c, u, s, r, e = rows[0]
        return RefreshToken(token=refresh_token, client_id=c, scopes=s.split(), expires_at=e, resource=r, subject=u)

    async def exchange_refresh_token(self, client: OAuthClientInformationFull, refresh_token: RefreshToken, scopes: list[str]) -> OAuthToken:
        self.store.q("delete from tokens where hash = ?", _h(refresh_token.token))  # rotation
        return self._issue(client.client_id, refresh_token.subject or "", scopes or refresh_token.scopes, refresh_token.resource)

    async def load_access_token(self, token: str) -> AccessToken | None:
        rows = self.store.q("select client_id, user_id, scopes, resource, expires_at from tokens where hash = ? and kind = 'access'",
                            _h(token))
        if not rows or rows[0][4] < time.time():
            return None
        c, u, s, r, e = rows[0]
        return AccessToken(token=token, client_id=c, scopes=s.split(), expires_at=e, resource=r, subject=u)

    async def revoke_token(self, token: AccessToken | RefreshToken) -> None:
        self.store.q("delete from tokens where hash = ?", _h(token.token))

    # -- session Supabase de l'utilisateur
    def _save_supabase(self, s: SupabaseSession) -> None:
        self.store.q("insert or replace into supa values (?, ?, ?, ?)", s.user_id, s.email,
                     self.store.enc(json.dumps({"a": s.access_token, "r": s.refresh_token})), s.expires_at)

    async def identity_for(self, user_id: str) -> Identity:
        lock = self._locks.setdefault(user_id, asyncio.Lock())
        async with lock:
            rows = self.store.q("select email, enc, expires_at from supa where user_id = ?", user_id)
            if not rows:
                raise AuthFailed("no_supabase_session")
            email, enc, exp = rows[0]
            tok = json.loads(self.store.dec(enc))
            if exp - 60 < time.time():
                s = await self.supabase.refresh(tok["r"])
                if s.user_id != user_id:
                    raise AuthFailed("user_mismatch")
                self._save_supabase(s)
                tok = {"a": s.access_token}
            return Identity(user_id=user_id, supabase_access_token=tok["a"])

    async def current_identity(self) -> Identity:
        at = get_access_token()
        if at is None or not at.subject:
            raise AuthFailed("unauthenticated")
        return await self.identity_for(at.subject)


def _page(title: str, body: str) -> str:
    return f"""<!doctype html><html lang=fr><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title><style>
body{{font-family:system-ui,sans-serif;max-width:420px;margin:48px auto;padding:0 16px;color:#1b1f24;background:#f6f7f9}}
h1{{font-size:22px}} label{{display:block;margin:14px 0 4px;font-weight:600}} input{{width:100%;padding:12px;font-size:16px;
border:1px solid #c8cdd3;border-radius:10px;box-sizing:border-box}} button{{margin-top:20px;width:100%;padding:14px;font-size:16px;
background:#0b6e4f;color:#fff;border:0;border-radius:10px}} .err{{color:#b00020;font-weight:600}}</style>
<h1>🏃 {html.escape(title)}</h1>{body}</html>"""
