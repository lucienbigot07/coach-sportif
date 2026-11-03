"""Application ASGI du serveur MCP distant (HTTP streamable) — point d'entrée : ``uvicorn coach_mcp.server:app``.

Variables d'environnement (voir .env.example ; aucune valeur secrète dans Git) :
  COACH_PUBLIC_URL        https://coach-mcp.example.fr  (URL publique, HTTPS)
  SUPABASE_URL            https://<projet>.supabase.co
  SUPABASE_ANON_KEY       clé publique « publishable/anon » (le RLS protège les données)
  COACH_ALLOWED_EMAILS    e-mails autorisés à connecter Claude, séparés par des virgules
  COACH_TOKEN_ENC_KEY     clé Fernet (scelle codes et jetons ; la changer déconnecte Claude)
Mode développement local, sans OAuth (refusé hors 127.0.0.1) :
  COACH_DEV_MODE=1  COACH_DEV_USER_ID=<uuid>  COACH_PG_DSN=postgresql://…
"""

from __future__ import annotations

import os
from urllib.parse import urlparse

from mcp.server.auth.settings import AuthSettings, ClientRegistrationOptions, RevocationOptions
from mcp.server.transport_security import TransportSecuritySettings
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse

from .auth import SCOPE, CoachOAuthProvider, HttpSupabaseAuth, Sealer, SupabaseAuthClient
from .backend import Backend, Identity, PsqlBackend, SupabaseBackend
from .tools import build_server


def create_app(*, public_url: str, backend: Backend, supabase_auth: SupabaseAuthClient, allowed_emails: set[str],
               enc_key: str) -> Starlette:
    provider = CoachOAuthProvider(Sealer(enc_key), supabase_auth, public_url, allowed_emails)
    auth = AuthSettings(
        issuer_url=public_url,
        resource_server_url=provider.resource,
        client_registration_options=ClientRegistrationOptions(enabled=True, valid_scopes=[SCOPE], default_scopes=[SCOPE]),
        revocation_options=RevocationOptions(enabled=False),
        validate_token_resource=True,
    )
    mcp = build_server(backend, provider.current_identity, auth_server_provider=provider, auth=auth)

    @mcp.custom_route("/login", methods=["GET", "POST"])
    async def login(request: Request):
        return await provider.login_page(request)

    @mcp.custom_route("/healthz", methods=["GET"])
    async def healthz(_: Request):
        return JSONResponse({"ok": True})

    host = urlparse(public_url).netloc
    return mcp.streamable_http_app(
        stateless_http=True, json_response=True,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=[host, "127.0.0.1:*", "localhost:*"],
            allowed_origins=[public_url, "https://claude.ai", "https://claude.com"]),
        host="0.0.0.0",
    )


def create_dev_app() -> Starlette:
    user = os.environ["COACH_DEV_USER_ID"]

    async def resolve() -> Identity:
        return Identity(user)

    mcp = build_server(PsqlBackend(os.environ.get("COACH_PG_DSN")), resolve)
    return mcp.streamable_http_app(stateless_http=True, json_response=True, host="127.0.0.1")


def _from_env() -> Starlette:
    if os.environ.get("COACH_DEV_MODE") == "1":
        return create_dev_app()
    public_url = os.environ["COACH_PUBLIC_URL"].rstrip("/")
    if not public_url.startswith("https://"):
        raise SystemExit("COACH_PUBLIC_URL doit être en HTTPS (Claude se connecte depuis Internet)")
    supabase_url, anon = os.environ["SUPABASE_URL"], os.environ["SUPABASE_ANON_KEY"]
    return create_app(
        public_url=public_url,
        backend=SupabaseBackend(supabase_url, anon),
        supabase_auth=HttpSupabaseAuth(supabase_url, anon),
        allowed_emails=set(os.environ["COACH_ALLOWED_EMAILS"].split(",")),
        enc_key=os.environ["COACH_TOKEN_ENC_KEY"],
    )


def __getattr__(name: str):  # `uvicorn coach_mcp.server:app` construit l'app à la demande
    if name == "app":
        return _from_env()
    raise AttributeError(name)
