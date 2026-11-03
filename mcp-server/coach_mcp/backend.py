"""Accès aux fonctions RPC Postgres (db/migrations/0003_functions.sql).

Deux implémentations, même contrat :

* ``SupabaseBackend`` — production : POST {SUPABASE_URL}/rest/v1/rpc/<fn> avec le JWT
  **de l'utilisateur** (jamais la clé service_role) ; le RLS et auth.uid() s'appliquent.
* ``PsqlBackend`` — développement/tests : appelle un Postgres local via ``psql`` en jouant
  le rôle ``authenticated`` avec les mêmes claims JWT que Supabase. Aucun pilote Python requis.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
import subprocess
from dataclasses import dataclass
from typing import Any, Protocol

try:  # httpx2 (installé avec le SDK MCP 2.x) ou httpx : même API
    import httpx2 as httpx
except ImportError:  # pragma: no cover
    import httpx  # type: ignore[no-redef]


@dataclass(frozen=True)
class Identity:
    """Utilisateur au nom duquel on appelle la base."""

    user_id: str
    supabase_access_token: str | None = None  # requis par SupabaseBackend


class RpcError(Exception):
    """Erreur métier renvoyée par une fonction SQL (errcode PTnnn) ou par le transport."""

    def __init__(self, status: int, code: str, detail: Any = None):
        super().__init__(f"{status} {code}")
        self.status = status
        self.code = code
        self.detail = detail

    def to_dict(self) -> dict[str, Any]:
        return {"success": False, "status": self.status, "error": self.code, "detail": self.detail}


class Backend(Protocol):
    async def call(self, fn: str, args: dict[str, Any], identity: Identity) -> Any: ...


_FN_RE = re.compile(r"^[a-z_][a-z0-9_]{0,62}$")


def _parse_detail(raw: str | None) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except (ValueError, TypeError):
        return raw


class SupabaseBackend:
    def __init__(self, url: str, anon_key: str, timeout: float = 15.0):
        if not url.startswith("https://") and not url.startswith("http://127.0.0.1"):
            raise ValueError("SUPABASE_URL doit être en HTTPS")
        self._base = url.rstrip("/") + "/rest/v1/rpc/"
        self._anon_key = anon_key  # clé publique (publishable/anon), pas un secret
        self._client = httpx.AsyncClient(timeout=timeout)

    async def call(self, fn: str, args: dict[str, Any], identity: Identity) -> Any:
        if not _FN_RE.match(fn):
            raise ValueError(fn)
        if not identity.supabase_access_token:
            raise RpcError(401, "not_authenticated")
        r = await self._client.post(
            self._base + fn,
            json={k: v for k, v in args.items() if v is not None},
            headers={
                "apikey": self._anon_key,
                "Authorization": f"Bearer {identity.supabase_access_token}",
                "Content-Type": "application/json",
                "Prefer": "return=representation",
            },
        )
        if r.status_code >= 400:
            try:
                body = r.json()
            except ValueError:
                body = {"message": r.text[:300]}
            raise RpcError(r.status_code, body.get("message") or "error", _parse_detail(body.get("details")))
        return r.json()


class PsqlBackend:
    """Backend de développement : psql en sous-processus, valeurs passées en variables psql (quotées)."""

    def __init__(self, dsn: str | None = None):
        self._dsn = dsn or os.environ.get("COACH_PG_DSN", "postgresql://postgres@127.0.0.1:54329/coach")

    def _run(self, fn: str, args: dict[str, Any], identity: Identity) -> Any:
        if not _FN_RE.match(fn):
            raise ValueError(fn)
        cmd = ["psql", self._dsn, "-X", "-q", "-t", "-A", "-v", "ON_ERROR_STOP=1", "-v", "VERBOSITY=verbose",
               "-v", f"claims={json.dumps({'sub': identity.user_id, 'role': 'authenticated'})}"]
        parts = []
        for k, v in args.items():
            if not _FN_RE.match(k):
                raise ValueError(k)
            if v is None:
                parts.append(f"{k} => NULL")
                continue
            val = json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else (
                ("true" if v else "false") if isinstance(v, bool) else str(v))
            cmd += ["-v", f"a_{k}={val}"]
            parts.append(f"{k} => :'a_{k}'")
        sql = (
            "\\set VERBOSITY verbose\n"
            "begin;\n"
            "set local role authenticated;\n"
            "select set_config('request.jwt.claims', :'claims', true) \\g /dev/null\n"
            f"select public.{fn}({', '.join(parts)})::text;\n"
            "commit;\n"
        )
        p = subprocess.run(cmd, input=sql, capture_output=True, text=True, timeout=30)
        if p.returncode != 0:
            err = p.stderr
            m = re.search(r"ERROR:\s+(\w{5}):\s+(.*)", err)
            if not m:
                raise RpcError(500, "database_error", err.strip()[-500:])
            sqlstate, message = m.group(1), m.group(2).strip()
            detail = None
            d = re.search(r"DETAIL:\s+(.*)", err)
            if d:
                detail = _parse_detail(d.group(1).strip())
            if sqlstate.startswith("PT") and sqlstate[2:].isdigit():
                raise RpcError(int(sqlstate[2:]), message, detail)
            if sqlstate == "42501":
                raise RpcError(403, "permission_denied", message)
            raise RpcError(400 if sqlstate.startswith(("22", "23")) else 500, message, {"sqlstate": sqlstate, **({"detail": detail} if detail else {})})
        out = p.stdout.strip()
        return json.loads(out) if out else None

    async def call(self, fn: str, args: dict[str, Any], identity: Identity) -> Any:
        return await asyncio.to_thread(self._run, fn, args, identity)

    # utilitaire de test : SQL brut en superutilisateur
    def admin_sql(self, sql: str) -> str:
        p = subprocess.run(["psql", self._dsn, "-X", "-q", "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", sql],
                           capture_output=True, text=True, timeout=30)
        if p.returncode != 0:
            raise RuntimeError(p.stderr)
        return p.stdout.strip()
