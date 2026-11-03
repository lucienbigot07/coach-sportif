"""Fixtures : Postgres 16 local jetable + stub Supabase + migrations + seed importé pour l'utilisateur A."""
import asyncio
import json
import pathlib
import subprocess
import uuid

import pytest

from coach_mcp.backend import Identity, PsqlBackend

ROOT = pathlib.Path(__file__).resolve().parents[2]
USER_A = Identity("aaaaaaaa-0000-4000-8000-000000000001")
USER_B = Identity("bbbbbbbb-0000-4000-8000-000000000002")


@pytest.fixture(scope="session")
def backend():
    out = subprocess.run([str(ROOT / "db/scripts/local_db.sh")], capture_output=True, text=True, check=True)
    dsn = out.stdout.strip().splitlines()[-1]
    be = PsqlBackend(dsn)
    be.admin_sql(f"insert into auth.users(id, email) values ('{USER_A.user_id}', 'a@test.local'), ('{USER_B.user_id}', 'b@test.local')")
    seed = json.loads((ROOT / "seed/03_DONNEES_INITIALES.json").read_text())
    res = asyncio.run(be.call("import_initial_dataset", {"p": seed}, USER_A))
    assert res["success"], res
    return be


def call(be, fn, ident=USER_A, **args):
    return asyncio.run(be.call(fn, args, ident))


def key():
    return "test-" + uuid.uuid4().hex
