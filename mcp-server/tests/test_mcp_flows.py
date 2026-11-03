"""Flux A et B de bout en bout à travers le protocole MCP (client MCP officiel ↔ serveur ↔ Postgres).

Base fraîche (fixture `fresh`) pour rejouer les deux scénarios de démonstration du cahier des charges.
"""
import asyncio
import json
import subprocess

import pytest
from mcp.client import Client

from coach_mcp.backend import PsqlBackend
from coach_mcp.tools import build_server
from conftest import ROOT, USER_A, call, key


@pytest.fixture(scope="module")
def fresh():
    out = subprocess.run([str(ROOT / "db/scripts/local_db.sh")], capture_output=True, text=True, check=True)
    be = PsqlBackend(out.stdout.strip().splitlines()[-1])
    be.admin_sql(f"insert into auth.users(id) values ('{USER_A.user_id}')")
    seed = json.loads((ROOT / "seed/03_DONNEES_INITIALES.json").read_text())
    call(be, "import_initial_dataset", p=seed)
    return be


def run(coro):
    return asyncio.run(coro)


async def _resolve():
    return USER_A


def payload(result):
    assert not result.is_error, result.content[0].text
    return result.structured_content  # dict renvoyé tel quel par l'outil


def test_outils_exposes(fresh):
    async def go():
        async with Client(build_server(fresh, _resolve)) as c:
            return {t.name: t for t in (await c.list_tools()).tools}
    tools = run(go())
    assert set(tools) == {"get_profile", "get_training_context", "list_week_plan", "get_day_plan", "get_exercise_history",
                          "get_recent_completed_sessions", "get_week_summary", "create_plan_draft", "publish_validated_plan",
                          "revise_session", "mark_event_confirmation", "record_user_reported_session"}
    assert tools["get_day_plan"].annotations.read_only_hint is True
    assert "user_approval_reference" in tools["revise_session"].input_schema["required"]


def test_scenario_A_claude_vers_app(fresh):
    """« Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide. »"""
    async def go():
        async with Client(build_server(fresh, _resolve)) as c:
            week = payload(await c.call_tool("list_week_plan", {"week_start": "2026-11-02"}))
            run_wed = next(a for d in week["days"] for a in d["activities"] if a["session_key"] == "20261104_run")
            assert run_wed["target"]["distance_m"] == 10000
            res = payload(await c.call_tool("revise_session", {
                "session_id": "20261104_run", "expected_version": run_wed["version"],
                "patch": {"target": {"distance_m": 8000}, "title": "8 km faciles"},
                "reason": "Allègement demandé par l'utilisateur",
                "user_approval_reference": "Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide."}))
            # même appel rejoué (réseau instable côté Claude) → pas de version n+2
            again = payload(await c.call_tool("revise_session", {
                "session_id": "20261104_run", "expected_version": run_wed["version"],
                "patch": {"target": {"distance_m": 8000}, "title": "8 km faciles"},
                "reason": "Allègement demandé par l'utilisateur",
                "user_approval_reference": "Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide."}))
            return res, again
    res, again = run(go())
    assert res["success"] is True and res["version"] == 2 and res["previous"]["target"]["distance_m"] == 10000
    assert again["replayed"] is True and again["version"] == 2
    # Ce que l'application lit (même RPC que l'app Android) :
    app_view = {a["session_key"]: a for a in call(fresh, "get_day_plan", p_date="2026-11-04")["activities"]}
    assert app_view["20261104_run"]["target"]["distance_m"] == 8000
    assert app_view["20261104_run"]["last_change"]["actor"] == "claude_mcp"
    monday = {a["session_key"]: a for a in call(fresh, "get_day_plan", p_date="2026-11-02")["activities"]}
    assert monday["20261102_run"]["result"]["endurance"]["duration_s"] == 1442


def test_scenario_B_app_vers_claude(fresh):
    """Android : 3 séries de tirage vertical sur la machine du 7/10 (66 kg) → Claude les relit."""
    app_payload = {"planned_session_key": "20261103_back", "date": "2026-11-03", "sport": "strength", "rpe_10": 7,
                   "sets": [{"variant_key": "vertical_row_machine_66", "exercise_position": 2, "set_index": i,
                             "load_kg": 66, "reps": r} for i, r in ((1, 10), (2, 10), (3, 10))]}
    r = call(fresh, "complete_activity", p_payload=app_payload, p_idempotency_key=key())  # appel fait par l'app
    assert r["success"]

    async def go():
        async with Client(build_server(fresh, _resolve)) as c:
            recent = payload(await c.call_tool("get_recent_completed_sessions", {"from_date": "2026-11-03"}))
            hist = payload(await c.call_tool("get_exercise_history", {"exercise_variant_id": "vertical_row_machine_66"}))
            other = payload(await c.call_tool("get_exercise_history", {"exercise_variant_id": "vertical_row_machine_120"}))
            return recent, hist, other
    recent, hist, other = run(go())
    s = recent["sessions"][0]
    assert s["planned_session_key"] == "20261103_back" and s["source"] == "app_logged"
    assert [x["reps"] for x in s["exercises"][0]["work_sets"]] == [10, 10, 10]
    assert hist["sessions"][0]["work_sets"][0]["load_kg"] == 66
    assert hist["reference"]["historical_reference"]["reference"]["sets"][0] == [66, 10]
    assert other["sessions"] == [] and other["reference"]["historical_reference"]["reference"]["sets"][0] == [120, 10]


def test_B3_B5_pas_de_fausse_synchronisation(fresh):
    async def go():
        async with Client(build_server(fresh, _resolve)) as c:
            no_approval = await c.call_tool("revise_session", {
                "session_id": "20261105_ham", "expected_version": 1, "patch": {"title": "5 km ?"},
                "reason": "peut-être", "user_approval_reference": ""})
            stale = await c.call_tool("revise_session", {
                "session_id": "20261104_run", "expected_version": 1, "patch": {"target": {"distance_m": 6000}},
                "reason": "test", "user_approval_reference": "je valide"})
            completed = await c.call_tool("mark_event_confirmation", {
                "event_id": "20261103_back", "confirmed_status": "confirmed", "user_approval_reference": "oui"})
            return no_approval, stale, completed
    no_approval, stale, completed = run(go())
    for r, code in ((no_approval, "approval_required"), (stale, "version_conflict"), (completed, "completed_activity_protected")):
        assert r.is_error
        body = json.loads(r.content[0].text.split(": ", 1)[1])  # « Error executing tool X: {json} »
        assert body["error"] == code and body["synchronized"] is False


def test_declaration_en_conversation(fresh):
    async def go():
        async with Client(build_server(fresh, _resolve)) as c:
            return payload(await c.call_tool("record_user_reported_session", {
                "user_approval_reference": "Piscine faite ce midi : 550 m en 22 min, ressenti 3/10",
                "session": {"planned_session_key": "20261103_swim", "date": "2026-11-03", "sport": "swimming",
                            "rpe_10": 3, "endurance": {"distance_m": 550, "duration_s": 1320}}}))
    r = run(go())
    assert r["success"] and r["source"] == "user_report"
    swim = {a["session_key"]: a for a in call(fresh, "get_day_plan", p_date="2026-11-03")["activities"]}["20261103_swim"]
    assert swim["status"] == "completed" and swim["result"]["endurance"]["distance_m"] == 550
    assert "avg_hr" not in swim["result"]["endurance"]  # rien d'inventé
