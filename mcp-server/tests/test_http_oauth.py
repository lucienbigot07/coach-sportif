"""Serveur MCP distant réel (uvicorn + HTTP streamable) : parcours OAuth 2.1 complet comme le ferait Claude,
puis appel d'outils avec le jeton obtenu. Seule l'API Supabase Auth est simulée (FakeSupabaseAuth)."""
import asyncio
import base64
import hashlib
import json
import secrets
import socket
import threading
import time
from urllib.parse import parse_qs, urlparse

import httpx2
import pytest
import requests
import uvicorn
from cryptography.fernet import Fernet
from mcp.client import Client
from mcp.client.streamable_http import streamable_http_client

from coach_mcp.auth import AuthFailed, SupabaseSession
from coach_mcp.server import create_app
from conftest import USER_A

CALLBACK = "https://claude.ai/api/mcp/auth_callback"


class FakeSupabaseAuth:
    """Imite Supabase : refresh token à usage unique (rotation)."""
    def __init__(self):
        self.refreshes = 0
        self.valid = {"rt-1"}

    async def password_login(self, email, password):
        if (email, password) != ("athlete@test.local", "bon-mot-de-passe"):
            raise AuthFailed("bad")
        return SupabaseSession(USER_A.user_id, email, "jwt-1", "rt-1", int(time.time()) + 3600)

    async def refresh(self, rt):
        if rt not in self.valid:
            raise AuthFailed("refresh token réutilisé")
        self.valid.discard(rt)
        self.refreshes += 1
        self.valid.add(f"rt-{self.refreshes + 1}")
        return SupabaseSession(USER_A.user_id, "athlete@test.local", f"jwt-{self.refreshes + 1}", f"rt-{self.refreshes + 1}",
                               int(time.time()) + 3600)


@pytest.fixture(scope="module")
def server(backend, tmp_path_factory):
    s = socket.socket(); s.bind(("127.0.0.1", 0)); port = s.getsockname()[1]; s.close()
    base = f"http://127.0.0.1:{port}"
    fake = FakeSupabaseAuth()
    key = Fernet.generate_key().decode()

    def start():
        app = create_app(public_url=base, backend=backend, supabase_auth=fake, allowed_emails={"athlete@test.local"},
                         enc_key=key)
        srv = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning"))
        t = threading.Thread(target=srv.run, daemon=True); t.start()
        for _ in range(200):
            try:
                requests.get(base + "/healthz", timeout=0.5); break
            except requests.ConnectionError:
                time.sleep(0.05)
        return srv, t

    state = {"srv": start()}

    def restart():  # simule un hébergeur gratuit qui s'endort / redémarre
        srv, t = state["srv"]; srv.should_exit = True; t.join(5)
        state["srv"] = start()

    yield base, fake, restart
    srv, t = state["srv"]; srv.should_exit = True; t.join(5)


def test_oauth_puis_outils(server):
    base, fake, restart = server
    s = requests.Session(); s.trust_env = False
    # 1. sans jeton → 401 + découverte
    r = s.post(base + "/mcp", json={"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
               headers={"Accept": "application/json, text/event-stream"})
    assert r.status_code == 401 and "resource_metadata" in r.headers["WWW-Authenticate"]
    prm = s.get(base + "/.well-known/oauth-protected-resource/mcp").json()
    assert prm["resource"] == base + "/mcp"
    meta = s.get(base + "/.well-known/oauth-authorization-server").json()
    assert "S256" in meta["code_challenge_methods_supported"]
    # 2. enregistrement dynamique (comme Claude)
    reg = s.post(meta["registration_endpoint"], json={"redirect_uris": [CALLBACK], "client_name": "Claude",
                 "token_endpoint_auth_method": "none", "grant_types": ["authorization_code", "refresh_token"],
                 "response_types": ["code"]}).json()
    cid = reg["client_id"]
    # 3. /authorize avec PKCE → page de connexion
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    r = s.get(meta["authorization_endpoint"], params={"response_type": "code", "client_id": cid, "redirect_uri": CALLBACK,
              "code_challenge": challenge, "code_challenge_method": "S256", "state": "etat-123",
              "resource": base + "/mcp"}, allow_redirects=False)
    assert r.status_code == 302
    login_url = r.headers["location"]
    req_id = parse_qs(urlparse(login_url).query)["req"][0]
    page = s.get(login_url)
    assert "Autoriser" in page.text
    # Chrome applique form-action aussi à la redirection qui suit l'envoi du formulaire :
    # sans claude.ai autorisé, l'utilisateur resterait bloqué sur la page de connexion.
    assert "https://claude.ai" in page.headers["Content-Security-Policy"]
    # 4. refus : mauvais mot de passe, e-mail non autorisé
    assert "Identifiants refusés" in s.post(base + "/login", data={"req": req_id, "email": "athlete@test.local",
                                                                   "password": "faux"}).text
    assert "Identifiants refusés" in s.post(base + "/login", data={"req": req_id, "email": "intrus@test.local",
                                                                   "password": "bon-mot-de-passe"}).text
    # 5. connexion → retour vers Claude avec code + state
    r = s.post(base + "/login", data={"req": req_id, "email": "athlete@test.local", "password": "bon-mot-de-passe"},
               allow_redirects=False)
    assert r.status_code == 302 and r.headers["location"].startswith(CALLBACK)
    q = parse_qs(urlparse(r.headers["location"]).query)
    assert q["state"] == ["etat-123"]
    # 6. échange du code (PKCE) ; code à usage unique
    form = {"grant_type": "authorization_code", "code": q["code"][0], "redirect_uri": CALLBACK, "client_id": cid,
            "code_verifier": verifier, "resource": base + "/mcp"}
    tok = s.post(meta["token_endpoint"], data=form).json()
    assert tok["token_type"].lower() == "bearer" and tok["refresh_token"]
    assert s.post(meta["token_endpoint"], data=form).status_code == 400
    bad = s.post(meta["token_endpoint"], data={**form, "code_verifier": "x" * 50})
    assert bad.status_code == 400

    # 7. appels MCP authentifiés sur le vrai transport HTTP
    async def use(access):
        async with httpx2.AsyncClient(headers={"Authorization": f"Bearer {access}"}, trust_env=False) as hc:
            async with Client(streamable_http_client(base + "/mcp", http_client=hc)) as c:
                tools = await c.list_tools()
                day = await c.call_tool("get_day_plan", {"date": "2026-11-02"})
                return len(tools.tools), day
    n, day = asyncio.run(use(tok["access_token"]))
    assert n == 12 and not day.is_error
    keys = {a["session_key"] for a in day.structured_content["activities"]}
    assert {"20261102_run", "20261102_quad"} <= keys
    restart()  # le jeton reste valide après redémarrage : aucun état perdu
    n_after, _ = asyncio.run(use(tok["access_token"]))
    assert n_after == 12

    # 8. refresh token rotatif ; jeton invalide refusé
    new = s.post(meta["token_endpoint"], data={"grant_type": "refresh_token", "refresh_token": tok["refresh_token"],
                                               "client_id": cid}).json()
    assert new["access_token"] != tok["access_token"]
    reused = s.post(meta["token_endpoint"], data={"grant_type": "refresh_token", "refresh_token": tok["refresh_token"],
                                                  "client_id": cid})
    assert reused.status_code == 400 and reused.json()["error"] == "invalid_grant"  # exigé par Claude
    r = s.post(base + "/mcp", json={"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
               headers={"Accept": "application/json, text/event-stream", "Authorization": "Bearer inventé"})
    assert r.status_code == 401
    n2, _ = asyncio.run(use(new["access_token"]))
    assert n2 == 12 and fake.refreshes == 1
    # inscription d'un client avec un retour hors Claude → refusé
    evil = s.post(meta["registration_endpoint"], json={"redirect_uris": ["https://pirate.example/cb"],
                  "token_endpoint_auth_method": "none", "grant_types": ["authorization_code"], "response_types": ["code"]})
    assert evil.status_code == 400
