"""Scénarios de recette (05_SCENARIOS_RECETTE.md) vérifiés directement sur les fonctions SQL.

Chaque test indique le scénario couvert. Exécutés sur un vrai Postgres 16 avec RLS forcé.
"""
import pytest

from coach_mcp.backend import RpcError
from conftest import USER_A, USER_B, call, key


def acts(day):
    return {a["session_key"]: a for a in day["activities"]}


# ---------------------------------------------------------------- A. historique & affichage
def test_A2_mardi_3_prevu_foot_a_confirmer(backend):
    d = acts(call(backend, "get_day_plan", p_date="2026-11-03"))
    assert set(d) == {"20261103_swim", "20261103_back", "20261103_football"}
    assert d["20261103_swim"]["status"] == "planned"
    assert d["20261103_swim"]["target"]["distance_m_range"] == [500, 600]
    assert d["20261103_back"]["status"] == "planned"
    assert d["20261103_football"]["status"] == "pending_confirmation"
    assert d["20261103_football"]["provisional_time_local"] == "19:30"
    assert "confirmed_time_local" not in d["20261103_football"]
    assert all("result" not in a for a in d.values())


def test_A2_dos_biceps_prerempli_avec_references_par_machine(backend):
    back = acts(call(backend, "get_day_plan", p_date="2026-11-03"))["20261103_back"]
    refs = {r["variant_key"]: r for r in back["split_references"]}
    assert "exercises" not in back  # aucune séance détaillée inventée
    assert refs["vertical_row_machine_66"]["reference"]["historical_reference"]["reference"]["sets"] == [[66, 10]] * 3
    assert refs["vertical_row_machine_120"]["reference"]["historical_reference"]["reference"]["sets"] == [[120, 10]] * 3
    # A5 : même nom, deux variantes distinctes
    assert refs["vertical_row_machine_66"]["name"] == refs["vertical_row_machine_120"]["name"] == "Tirage vertical"


def test_A3_lundi_2_realise_avec_ecart_d_intensite(backend):
    d = acts(call(backend, "get_day_plan", p_date="2026-11-02"))
    run = d["20261102_run"]
    assert run["status"] == "completed"
    assert run["target"]["distance_m"] == 5000 and run["target"]["pace_s_per_km_range"] == [360, 395]
    e = run["result"]["endurance"]
    assert (e["distance_m"], e["duration_s"], e["avg_hr"], e["ascent_m"]) == (5000, 1442, 166, 21)
    assert e["pace_s_per_km"] == 288.4  # 4:48/km
    assert run["result"]["rpe_10"] == 6
    assert run["result"]["planned_session_type"] == "easy" and run["result"]["actual_session_type"] == "sustained"
    quad = d["20261102_quad"]["result"]
    assert quad["work_sets_count"] == 21 and len(quad["exercises"]) == 7


def test_A4_fentes_44kg_et_objectif_non_atteint(backend):
    h = call(backend, "get_exercise_history", p_variant_key="walking_lunges_44", p_limit=5)
    last = h["reference"]["last_logged"]
    assert last["date"] == "2026-11-02"
    assert [(s["load_kg"], s["steps"]) for s in last["sets"]] == [(44, 32), (44, 33), (44, 33)]
    assert all("reps" not in s for s in last["sets"])  # des pas, pas des répétitions
    assert h["reference"]["future_target_not_validated"]["steps_each"] == 33


def test_A6_rdl_par_main_objectif_12_non_valide(backend):
    h = call(backend, "get_exercise_history", p_variant_key="rdl_dumbbells")
    assert h["variant"]["load_basis"] == "per_hand"
    assert h["reference"]["historical_reference"]["reference"]["sets"] == [[30, 10]] * 3
    assert h["reference"]["future_target_not_validated"]["reps_each"] == 12
    assert h["sessions"] == []  # aucun record inventé


def test_A7_trail_envisage_et_match(backend):
    w = call(backend, "list_week_plan", p_week_start="2026-11-02")
    days = {d["date"]: d for d in w["days"]}
    sat = {a["session_key"]: a for a in days["2026-11-07"]["activities"]}
    assert sat["20261107_trail"]["status"] == "pending_confirmation"
    sun = {a["session_key"]: a for a in days["2026-11-08"]["activities"]}
    assert sun["20261108_match"]["status"] == "planned"
    ev = {e["key"]: e for e in w["events"]}
    assert ev["corps_nuds_night"]["registration_status"] == "envisaged_unconfirmed"


def test_A8_jour_sans_seance(backend):
    r = call(backend, "get_day_plan", p_date="2026-11-12")
    assert r["activities"] == [] and r["message"] == "Aucune séance publiée pour ce jour"


def test_C7_dates_evenements(backend):
    evs = {e["key"]: e for e in call(backend, "get_profile")["events"]}
    assert evs["corps_nuds_night"]["event_date"] == "2026-11-07"
    assert evs["football_match_2026-11-08"]["event_date"] == "2026-11-08"
    assert evs["maine_libre_10k"]["event_date"] == "2026-11-21"
    assert evs["maine_libre_10k"]["registration_status"] == "confirmed"


def test_C8_inconnus_restent_null(backend):
    s = {x["key"]: x for x in call(backend, "get_recent_completed_sessions", p_limit=50)["sessions"]}
    e = s["run_2026_09_22"]["endurance"]
    assert e["distance_m"] == 4480 and "duration_s" not in e and "pace_s_per_km" not in e
    assert "rpe_10" not in s["run_2026_10_26"]
    prof = call(backend, "get_profile")["profile"]
    assert prof["current_weight_kg"] is None


def test_semaine_resume_prevu_vs_realise(backend):
    s = call(backend, "get_week_summary", p_week_start="2026-11-04")  # n'importe quel jour → lundi
    assert s["week_start"] == "2026-11-02"
    assert s["planned"]["running_km_firm"] == 15.0          # 5 (lundi) + 10 (mercredi)
    assert s["planned"]["running_km_pending_confirmation"] == 16.0  # trail envisagé
    assert s["planned"]["strength_sessions"] == 4
    assert s["actual"]["running_km"] == 5.0 and s["actual"]["strength_work_sets"] == 21


# ---------------------------------------------------------------- B. synchronisation (couche données)
def test_B2_revision_10_vers_8_km_versionnee(backend):
    before = acts(call(backend, "get_day_plan", p_date="2026-11-04"))["20261104_run"]
    assert before["target"]["distance_m"] == 10000 and before["version"] == 1
    k = key()
    r = call(backend, "revise_session", p_session_key="20261104_run", p_expected_version=1,
             p_patch={"target": {"distance_m": 8000}, "title": "8 km faciles"},
             p_reason="Remplacement des 10 km par 8 km faciles décidé en conversation",
             p_user_approval_reference="Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide.",
             p_idempotency_key=k)
    assert r["success"] and r["version"] == 2 and r["previous"]["target"]["distance_m"] == 10000
    after = acts(call(backend, "get_day_plan", p_date="2026-11-04"))["20261104_run"]
    assert after["target"]["distance_m"] == 8000 and after["title"] == "8 km faciles"
    assert after["last_change"]["previous"]["target"]["distance_m"] == 10000
    assert after["target"]["pace_s_per_km_range"] == [360, 395]  # le reste de la prescription est conservé
    # C2 : rejeu identique = pas de double version
    r2 = call(backend, "revise_session", p_session_key="20261104_run", p_expected_version=1,
              p_patch={"target": {"distance_m": 8000}, "title": "8 km faciles"},
              p_reason="Remplacement des 10 km par 8 km faciles décidé en conversation",
              p_user_approval_reference="Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide.",
              p_idempotency_key=k)
    assert r2["replayed"] is True and r2["version"] == 2
    # B2/A3 : lundi inchangé
    mon = acts(call(backend, "get_day_plan", p_date="2026-11-02"))["20261102_run"]
    assert mon["result"]["endurance"]["duration_s"] == 1442 and mon["version"] == 1


def test_C3_version_obsolete_409(backend):
    with pytest.raises(RpcError) as e:
        call(backend, "revise_session", p_session_key="20261104_run", p_expected_version=1,
             p_patch={"target": {"distance_m": 6000}}, p_reason="test conflit",
             p_user_approval_reference="je valide", p_idempotency_key=key())
    assert e.value.status == 409 and e.value.code == "version_conflict"
    assert e.value.detail["current"] == 2 and e.value.detail["current_state"]["target"]["distance_m"] == 8000


def test_B3_sans_accord_pas_de_publication(backend):
    with pytest.raises(RpcError) as e:
        call(backend, "revise_session", p_session_key="20261105_ham", p_expected_version=1,
             p_patch={"title": "5 km ?"}, p_reason="peut-être 5 km jeudi",
             p_user_approval_reference="", p_idempotency_key=key())
    assert e.value.code == "approval_required"
    # un brouillon reste invisible côté application
    dr = call(backend, "create_plan_draft", p_week_start="2026-11-09", p_rationale="idée",
              p_activities=[{"date": "2026-11-12", "sport": "running", "title": "5 km ?", "target": {"distance_m": 5000}}],
              p_idempotency_key=key())
    assert dr["status"] == "draft"
    assert call(backend, "get_day_plan", p_date="2026-11-12")["activities"] == []


def test_B6_seance_terminee_protegee(backend):
    with pytest.raises(RpcError) as e:
        call(backend, "revise_session", p_session_key="20261102_run", p_expected_version=1,
             p_patch={"target": {"distance_m": 3000}}, p_reason="test", p_user_approval_reference="je valide",
             p_idempotency_key=key())
    assert e.value.status == 409 and e.value.code == "completed_activity_protected"
    with pytest.raises(RpcError) as e:
        call(backend, "revise_session", p_session_key="20261104_chest", p_expected_version=1,
             p_patch={"status": "completed"}, p_reason="test", p_user_approval_reference="je valide",
             p_idempotency_key=key())
    assert e.value.code == "status_not_allowed"


def test_confirmation_foot_ne_vaut_pas_realisation(backend):
    with pytest.raises(RpcError):
        call(backend, "mark_event_confirmation", p_ref_key="20261103_football", p_confirmed_status="completed",
             p_user_approval_reference="oui", p_idempotency_key=key())
    r = call(backend, "mark_event_confirmation", p_ref_key="20261103_football", p_confirmed_status="confirmed",
             p_user_approval_reference="Le foot de ce soir est confirmé", p_confirmed_time_local="19:30",
             p_idempotency_key=key())
    assert r["status"] == "planned"
    foot = acts(call(backend, "get_day_plan", p_date="2026-11-03"))["20261103_football"]
    assert foot["status"] == "planned" and foot["confirmed_time_local"] == "19:30" and "result" not in foot


def test_B4_saisie_android_puis_lecture_coach(backend):
    payload = {
        "planned_session_key": "20261103_back", "date": "2026-11-03", "sport": "strength", "rpe_10": 7,
        "sets": [
            {"variant_key": "vertical_row_machine_66", "exercise_position": 2, "set_index": 1, "is_warmup": True, "load_kg": 40, "reps": 10},
            {"variant_key": "vertical_row_machine_66", "exercise_position": 2, "set_index": 1, "load_kg": 66, "reps": 10},
            {"variant_key": "vertical_row_machine_66", "exercise_position": 2, "set_index": 2, "load_kg": 66, "reps": 10},
            {"variant_key": "vertical_row_machine_66", "exercise_position": 2, "set_index": 3, "load_kg": 66, "reps": 9},
        ],
    }
    k = key()
    r = call(backend, "complete_activity", p_payload=payload, p_idempotency_key=k)
    assert r["success"] and r["result"]["work_sets_count"] == 3
    # C1/C2 : renvoi après coupure réseau → aucune double insertion
    r2 = call(backend, "complete_activity", p_payload=payload, p_idempotency_key=k)
    assert r2["replayed"] and r2["completed_activity_id"] == r["completed_activity_id"]
    with pytest.raises(RpcError) as e:  # nouvelle clé mais séance déjà terminée
        call(backend, "complete_activity", p_payload=payload, p_idempotency_key=key())
    assert e.value.code == "already_completed"

    h = call(backend, "get_exercise_history", p_variant_key="vertical_row_machine_66")
    assert h["sessions"][0]["date"] == "2026-11-03"
    assert [s["reps"] for s in h["sessions"][0]["work_sets"]] == [10, 10, 9]
    assert h["sessions"][0]["warmup_sets_count"] == 1
    assert "vertical_row_machine_120" in h["same_name_other_variants"]
    other = call(backend, "get_exercise_history", p_variant_key="vertical_row_machine_120")
    assert other["sessions"] == []  # la machine à 120 kg n'est pas polluée
    recent = call(backend, "get_recent_completed_sessions", p_from_date="2026-11-03")["sessions"]
    assert recent[0]["planned_session_key"] == "20261103_back" and recent[0]["source"] == "app_logged"
    day = acts(call(backend, "get_day_plan", p_date="2026-11-03"))
    assert day["20261103_back"]["status"] == "completed"


def test_flux_C_correction_conserve_l_historique(backend):
    s = call(backend, "get_recent_completed_sessions", p_from_date="2026-11-03")["sessions"][0]
    r = call(backend, "correct_completed_activity", p_completed_activity_id=s["completed_activity_id"],
             p_expected_version=1, p_patch={"rpe_10": 8}, p_reason="RPE mal saisi", p_idempotency_key=key())
    assert r["version"] == 2 and r["result"]["rpe_10"] == 8
    assert r["result"]["work_sets_count"] == 3  # séries intactes
    with pytest.raises(RpcError):
        call(backend, "correct_completed_activity", p_completed_activity_id=s["completed_activity_id"],
             p_expected_version=1, p_patch={"rpe_10": 5}, p_reason="obsolète", p_idempotency_key=key())


def test_validation_resultats(backend):
    with pytest.raises(RpcError) as e:  # l'échauffement seul n'est pas une séance de travail
        call(backend, "complete_activity", p_idempotency_key=key(), p_payload={
            "date": "2026-11-03", "sport": "strength",
            "sets": [{"variant_key": "pullover", "set_index": 1, "is_warmup": True, "load_kg": 20, "reps": 10}]})
    assert e.value.code == "work_sets_required"
    with pytest.raises(RpcError) as e:
        call(backend, "complete_activity", p_idempotency_key=key(), p_payload={
            "date": "2026-11-03", "sport": "strength",
            "sets": [{"variant_key": "tirage_inconnu", "set_index": 1, "load_kg": 20, "reps": 10}]})
    assert e.value.code == "unknown_variant"


def test_publication_semaine_complete_preserve_le_realise(backend):
    w = call(backend, "list_week_plan", p_week_start="2026-11-02")
    acts_all = [a for d in w["days"] for a in d["activities"]]
    keep = [a for a in acts_all if a["status"] not in ("completed",)]
    new = [{"key": a["session_key"], "date": a["date"], "sport": a["sport"], "subtype": a.get("subtype"),
            "title": a["title"], "target": a["target"],
            "status": "pending_confirmation" if a.get("confirmation_required") else "planned"} for a in keep]
    # Tentative de réécrire une séance réalisée → refus
    bad = new + [{"key": "20261102_run", "date": "2026-11-02", "sport": "running", "title": "10 km", "target": {"distance_m": 10000}}]
    d1 = call(backend, "create_plan_draft", p_week_start="2026-11-02", p_activities=bad, p_rationale="test", p_idempotency_key=key())
    with pytest.raises(RpcError) as e:
        call(backend, "publish_validated_plan", p_plan_id=d1["plan_id"], p_expected_version=1,
             p_user_approval_reference="je valide", p_idempotency_key=key())
    assert e.value.code == "completed_activity_protected"
    # Publication propre : le réalisé est reporté, la version du plan avance
    d2 = call(backend, "create_plan_draft", p_week_start="2026-11-02", p_activities=new, p_rationale="test", p_idempotency_key=key())
    p = call(backend, "publish_validated_plan", p_plan_id=d2["plan_id"], p_expected_version=1,
             p_user_approval_reference="Je valide la semaine", p_idempotency_key=key())
    assert p["success"] and p["version"] > w["plan_version"]
    assert set(p["preserved_completed_sessions"]) >= {"20261102_run", "20261102_quad", "20261103_back"}
    mon = acts(call(backend, "get_day_plan", p_date="2026-11-02"))
    assert mon["20261102_run"]["result"]["endurance"]["duration_s"] == 1442
    wed = acts(call(backend, "get_day_plan", p_date="2026-11-04"))
    assert wed["20261104_run"]["target"]["distance_m"] == 8000
    sat = acts(call(backend, "get_day_plan", p_date="2026-11-07"))
    assert sat["20261107_trail"]["status"] == "pending_confirmation"


# ---------------------------------------------------------------- C. sécurité
def test_C4_isolation_entre_comptes(backend):
    assert call(backend, "get_day_plan", USER_B, p_date="2026-11-03")["activities"] == []
    assert call(backend, "get_recent_completed_sessions", USER_B, p_limit=50)["sessions"] == []
    with pytest.raises(RpcError) as e:
        call(backend, "revise_session", USER_B, p_session_key="20261104_run", p_expected_version=2,
             p_patch={"title": "piratage"}, p_reason="test", p_user_approval_reference="je valide", p_idempotency_key=key())
    assert e.value.status == 404
    with pytest.raises(RpcError):
        call(backend, "get_exercise_history", USER_B, p_variant_key="walking_lunges_44")
    # RLS direct : B ne voit aucune ligne de A, même en SELECT brut
    n = backend.admin_sql(
        "begin; set local role authenticated; "
        f"select set_config('request.jwt.claims', '{{\"sub\":\"{USER_B.user_id}\"}}', true); "
        "select count(*) from completed_activities; commit;")
    assert n.splitlines()[-1] == "0"


def test_C4_pas_d_ecriture_directe_ni_anonyme(backend):
    with pytest.raises(RuntimeError):
        backend.admin_sql(
            "begin; set local role authenticated; "
            f"select set_config('request.jwt.claims', '{{\"sub\":\"{USER_A.user_id}\"}}', true); "
            "update planned_activities set status = 'completed'; commit;")
    with pytest.raises(RuntimeError):
        backend.admin_sql("begin; set local role anon; select get_day_plan('2026-11-03'); commit;")
    with pytest.raises(RuntimeError):
        backend.admin_sql("begin; set local role authenticated; select coach_private.uid(); commit;")


def test_C6_export_et_suppression(backend):
    ex = call(backend, "export_my_data")
    assert len(ex["exercise_variants"]) == 31 and len(ex["completed_sets"]) >= 24
    assert all("user_id" not in r for r in ex["completed_activities"])
    with pytest.raises(RpcError):
        call(backend, "delete_my_data", USER_B, p_confirmation="oui")
    r = call(backend, "delete_my_data", USER_B, p_confirmation="SUPPRIMER TOUTES MES DONNEES")
    assert r["success"]  # B n'a rien ; les données de A sont intactes
    assert call(backend, "export_my_data")["athlete_profiles"]


def test_republication_conserve_les_seances_pas_faites(backend):
    w = call(backend, "list_week_plan", p_week_start="2026-11-02")
    swim = next(a for d in w["days"] for a in d["activities"] if a["session_key"] == "20261106_swim")
    call(backend, "revise_session", p_session_key="20261106_swim", p_expected_version=swim["version"],
         p_patch={"status": "skipped", "notes": "Pas fait : Pas disponible"}, p_reason="Signalé dans l'application",
         p_user_approval_reference="Déclaré par l'utilisateur dans l'application", p_idempotency_key=key(), p_actor="android_app")
    w = call(backend, "list_week_plan", p_week_start="2026-11-02")
    keep = [a for d in w["days"] for a in d["activities"] if a["status"] not in ("completed", "skipped", "cancelled")]
    acts = [{"key": a["session_key"], "date": a["date"], "sport": a["sport"], "subtype": a.get("subtype"), "title": a["title"],
             "target": a["target"], "status": "pending_confirmation" if a.get("confirmation_required") else "planned"} for a in keep]
    dr = call(backend, "create_plan_draft", p_week_start="2026-11-02", p_activities=acts, p_rationale="test", p_idempotency_key=key())
    p = call(backend, "publish_validated_plan", p_plan_id=dr["plan_id"], p_expected_version=1,
             p_user_approval_reference="je valide", p_idempotency_key=key())
    assert "20261106_swim" in p["preserved_completed_sessions"]
    fri = {a["session_key"]: a for a in call(backend, "get_day_plan", p_date="2026-11-06")["activities"]}
    assert fri["20261106_swim"]["status"] == "skipped"
