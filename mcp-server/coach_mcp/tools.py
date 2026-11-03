"""Outils MCP du coach : contrat du cahier des charges §5 (+ déclaration de résultats donnés en conversation).

Règles appliquées ici (en plus de la base) :
* lecture avant décision — les descriptions d'outils le rappellent à Claude ;
* écriture = accord explicite cité (user_approval_reference), sinon refus côté base ;
* aucune réponse « synchronisé » sans ``success: true`` renvoyé par la base ;
* idempotence : clé fournie par Claude, ou dérivée du contenu exact de l'appel (un réessai identique est rejoué).
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
from collections.abc import Awaitable, Callable
from typing import Annotated, Any, Literal

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp_types import ToolAnnotations
from pydantic import BaseModel, ConfigDict, Field

from .backend import Backend, Identity, RpcError

IdentityResolver = Callable[[], Awaitable[Identity]]

Sport = Literal["running", "swimming", "strength", "football", "hyrox", "cycling", "walking", "mobility", "other"]
HHMM = Annotated[str, Field(pattern=r"^([01]\d|2[0-3]):[0-5]\d$")]

INSTRUCTIONS = """Serveur du Coach Sportif : base partagée entre cette conversation et l'application Android.
- Avant toute décision qui dépend du planning ou des performances : get_training_context, list_week_plan,
  get_recent_completed_sessions. La mémoire de la conversation n'est pas la source de vérité.
- Une séance prévue n'est jamais réalisée. « Envisagé / à confirmer » reste pending_confirmation.
- N'écrire (publish_validated_plan, revise_session, mark_event_confirmation, record_user_reported_session)
  qu'après un accord explicite de l'utilisateur, cité mot pour mot dans user_approval_reference.
- Annoncer « synchronisé » uniquement si la réponse contient success: true. Sinon, dire que la décision
  n'est PAS publiée dans l'application.
- Comparer une charge uniquement à la même variante/machine (variant_key)."""


class NewVariant(BaseModel):
    model_config = ConfigDict(extra="forbid")
    key: Annotated[str, Field(pattern=r"^[a-z0-9_]{3,60}$", description="identifiant stable, ex. chest_press_basicfit_rennes")]
    name: str
    split: str | None = None
    equipment_variant: str | None = Field(None, description="machine/appareil précis : sert à ne pas comparer deux machines")
    location: str | None = None
    load_basis: Literal["total", "per_hand", "machine", "bodyweight", "unknown"] = "unknown"
    performance_unit: Literal["reps", "steps", "seconds", "distance_m", "round_trips"] = "reps"
    notes: str | None = None


class ExerciseTarget(BaseModel):
    model_config = ConfigDict(extra="forbid")
    variant_key: str | None = Field(None, description="variante existante (voir split_references / get_exercise_history)")
    new_variant: NewVariant | None = Field(None, description="seulement si la machine/variante n'existe pas encore")
    sets: Annotated[int, Field(ge=1, le=10)] | None = None
    reps: int | None = None
    steps: int | None = None
    seconds: int | None = None
    load_kg: float | None = Field(None, description="charge visée ; préciser par main/total via la variante")
    rir_min: int | None = None
    rir_max: int | None = None
    is_priority: bool = Field(False, description="exercice 🔴 de progression (un par séance)")
    notes: str | None = None


class PlannedActivityIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    key: Annotated[str, Field(pattern=r"^[A-Za-z0-9_-]{3,60}$")] | None = Field(
        None, description="clé stable ex. 20261104_run ; réutiliser la clé existante pour remplacer une séance")
    date: dt.date
    sport: Sport
    subtype: str | None = Field(None, description="split muscu (dos_biceps, quadriceps, pecs_triceps, ischios_fessiers), match, trail…")
    title: str
    sort_order: int | None = None
    status: Literal["planned", "pending_confirmation"] = Field(
        "planned", description="pending_confirmation pour tout ce qui est envisagé / à confirmer")
    provisional_time_local: HHMM | None = Field(None, description="heure envisagée, non garantie")
    confirmed_time_local: HHMM | None = Field(None, description="uniquement si l'heure est confirmée")
    target: dict[str, Any] = Field(default_factory=dict, description="prescription : distance_m, pace_s_per_km_range, rpe_10_range, duration_min_range, intensity, avoid…")
    notes: str | None = None
    exercises: list[ExerciseTarget] = Field(default_factory=list)


class SessionPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    target: dict[str, Any] | None = Field(None, description="fusion : seules les clés fournies changent ; null supprime une clé")
    target_replace: dict[str, Any] | None = Field(None, description="remplace toute la prescription")
    notes: str | None = None
    activity_date: dt.date | None = Field(None, description="déplacement dans la même semaine")
    sort_order: int | None = None
    provisional_time_local: HHMM | None = None
    confirmed_time_local: HHMM | None = None
    status: Literal["planned", "pending_confirmation", "cancelled", "skipped"] | None = None
    exercises: list[ExerciseTarget] | None = Field(None, description="remplace la liste d'exercices prescrits")


class ReportedSet(BaseModel):
    model_config = ConfigDict(extra="forbid")
    variant_key: str | None = None
    new_variant: NewVariant | None = None
    exercise_position: int | None = None
    set_index: Annotated[int, Field(ge=1)]
    is_warmup: bool = False
    load_kg: float | None = None
    load_basis: Literal["total", "per_hand", "machine", "bodyweight", "unknown"] | None = None
    reps: int | None = None
    steps: int | None = Field(None, description="fentes marchées : des PAS, pas des répétitions")
    seconds: int | None = None
    distance_m: float | None = None
    rir: int | None = None
    notes: str | None = None


class EnduranceIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    distance_m: float | None = None
    duration_s: int | None = None
    ascent_m: int | None = None
    avg_hr: int | None = None
    max_hr: int | None = None
    effort_type: str | None = Field(None, description="facile | tempo | fractionné | course | match… ; ne pas déduire « facile » d'un RPE bas")
    surface: str | None = None
    pool_length_m: int | None = None


class ReportedSession(BaseModel):
    model_config = ConfigDict(extra="forbid")
    planned_session_key: str | None = Field(None, description="séance prévue correspondante, si elle existe")
    date: dt.date
    sport: Sport
    subtype: str | None = None
    rpe_10: Annotated[float, Field(ge=1, le=10)] | None = None
    actual_session_type: str | None = None
    notes: str | None = None
    sets: list[ReportedSet] = Field(default_factory=list)
    endurance: EnduranceIn | None = None


def _dump(m: BaseModel | list[BaseModel] | None) -> Any:
    if m is None:
        return None
    if isinstance(m, list):
        return [x.model_dump(mode="json", exclude_none=True) for x in m]
    return m.model_dump(mode="json", exclude_none=True)


def _auto_key(tool: str, payload: dict[str, Any]) -> str:
    raw = json.dumps({"tool": tool, **payload}, sort_keys=True, default=str, ensure_ascii=False)
    return "auto-" + hashlib.sha256(raw.encode()).hexdigest()[:40]


READ = ToolAnnotations(read_only_hint=True, open_world_hint=False)
WRITE = ToolAnnotations(read_only_hint=False, destructive_hint=False, idempotent_hint=True, open_world_hint=False)


def build_server(backend: Backend, resolve_identity: IdentityResolver, **server_kwargs: Any) -> MCPServer:
    mcp = MCPServer(name="coach-sportif", title="Coach Sportif", version="0.1.0", instructions=INSTRUCTIONS, **server_kwargs)

    async def rpc(fn: str, **args: Any) -> Any:
        ident = await resolve_identity()
        try:
            return await backend.call(fn, args, ident)
        except RpcError as e:
            body = e.to_dict()
            body["synchronized"] = False
            body["message_for_user"] = "Rien n'a été modifié dans l'application (erreur ci-dessus)."
            raise ToolError(json.dumps(body, ensure_ascii=False, default=str)) from None

    # ------------------------------------------------------------ lecture
    @mcp.tool(annotations=READ, description="Profil, objectifs, événements et dernières mesures Garmin (estimations datées).")
    async def get_profile() -> dict[str, Any]:
        return await rpc("get_profile")

    @mcp.tool(annotations=READ, description=(
        "Contexte d'entraînement : prévu et réalisé sur une fenêtre (défaut : J-14 à J+14), séances passées sans résultat, "
        "éléments à confirmer, événements. À appeler AVANT de proposer ou modifier une séance."))
    async def get_training_context(
        from_date: Annotated[dt.date | None, Field(description="YYYY-MM-DD, Europe/Paris")] = None,
        to_date: dt.date | None = None,
    ) -> dict[str, Any]:
        return await rpc("get_training_context", p_from=_d(from_date), p_to=_d(to_date))

    @mcp.tool(annotations=READ, description=(
        "Planning validé d'une semaine (lundi→dimanche) avec statut, version et résultats éventuels de chaque activité. "
        "include_drafts=true montre aussi les brouillons non publiés."))
    async def list_week_plan(week_start: Annotated[dt.date, Field(description="n'importe quel jour de la semaine")],
                             include_drafts: bool = False) -> dict[str, Any]:
        return await rpc("list_week_plan", p_week_start=_d(week_start), p_include_drafts=include_drafts)

    @mcp.tool(annotations=READ, description=(
        "Activités publiées d'un jour, avec exercices prescrits et références par variante (dernière séance journalisée, "
        "référence historique, objectif non validé)."))
    async def get_day_plan(date: dt.date) -> dict[str, Any]:
        return await rpc("get_day_plan", p_date=_d(date))

    @mcp.tool(annotations=READ, description=(
        "Historique série par série d'UNE variante (machine précise). Signale les autres variantes du même nom, "
        "à ne pas comparer."))
    async def get_exercise_history(exercise_variant_id: Annotated[str, Field(description="variant_key, ex. vertical_row_machine_66")],
                                   limit: Annotated[int, Field(ge=1, le=100)] = 10) -> dict[str, Any]:
        return await rpc("get_exercise_history", p_variant_key=exercise_variant_id, p_limit=limit)

    @mcp.tool(annotations=READ, description=(
        "Séances réellement réalisées (saisies dans l'app, déclarées ou historiques), les plus récentes d'abord. "
        "Appeler avant de commenter une séance : ne jamais supposer son contenu."))
    async def get_recent_completed_sessions(from_date: dt.date | None = None,
                                            limit: Annotated[int, Field(ge=1, le=100)] = 10) -> dict[str, Any]:
        return await rpc("get_recent_completed_sessions", p_from_date=_d(from_date), p_limit=limit)

    @mcp.tool(annotations=READ, description="Bilan prévu vs réalisé d'une semaine (km ferme / à confirmer, muscu, natation, foot).")
    async def get_week_summary(week_start: dt.date) -> dict[str, Any]:
        return await rpc("get_week_summary", p_week_start=_d(week_start))

    # ------------------------------------------------------------ écriture coach
    @mcp.tool(annotations=WRITE, description=(
        "Crée un BROUILLON de semaine, invisible dans l'application. Utiliser pour une proposition encore en discussion."))
    async def create_plan_draft(week_start: dt.date, activities: list[PlannedActivityIn], rationale: str,
                                idempotency_key: str | None = None) -> dict[str, Any]:
        acts = _dump(activities)
        k = idempotency_key or _auto_key("create_plan_draft", {"w": str(week_start), "a": acts, "r": rationale})
        return await rpc("create_plan_draft", p_week_start=_d(week_start), p_activities=acts, p_rationale=rationale,
                         p_idempotency_key=k)

    @mcp.tool(annotations=WRITE, description=(
        "Publie un brouillon comme planning VALIDÉ de la semaine (visible dans l'app). Remplace le planning validé "
        "précédent de cette semaine : les séances terminées sont conservées, les autres absentes du brouillon sont retirées. "
        "Pour changer une seule séance, préférer revise_session. Exige l'accord explicite cité."))
    async def publish_validated_plan(
        plan_id: str, expected_version: int,
        user_approval_reference: Annotated[str, Field(description="citation exacte de l'accord de l'utilisateur")],
        activities: Annotated[list[PlannedActivityIn] | None, Field(description="version finale si elle diffère du brouillon")] = None,
        idempotency_key: str | None = None,
    ) -> dict[str, Any]:
        acts = _dump(activities)
        k = idempotency_key or _auto_key("publish_validated_plan", {"p": plan_id, "v": expected_version, "a": acts,
                                                                    "ap": user_approval_reference})
        return await rpc("publish_validated_plan", p_plan_id=plan_id, p_expected_version=expected_version,
                         p_activities=acts, p_user_approval_reference=user_approval_reference, p_idempotency_key=k,
                         p_actor="claude_mcp")

    @mcp.tool(annotations=WRITE, description=(
        "Ajuste une séance publiée (ex. 10 km → 8 km) avec version attendue, raison et accord cité. L'ancienne valeur "
        "reste dans l'historique. Refusé sur une séance terminée. En cas de 409 version_conflict : relire et reproposer."))
    async def revise_session(
        session_id: Annotated[str, Field(description="session_key, ex. 20261104_run")],
        expected_version: int, patch: SessionPatch, reason: str,
        user_approval_reference: Annotated[str, Field(description="citation exacte de l'accord de l'utilisateur")],
        idempotency_key: str | None = None,
    ) -> dict[str, Any]:
        p = patch.model_dump(mode="json", exclude_unset=True)
        k = idempotency_key or _auto_key("revise_session", {"s": session_id, "v": expected_version, "p": p, "r": reason,
                                                            "ap": user_approval_reference})
        return await rpc("revise_session", p_session_key=session_id, p_expected_version=expected_version, p_patch=p,
                         p_reason=reason, p_user_approval_reference=user_approval_reference, p_idempotency_key=k,
                         p_actor="claude_mcp")

    @mcp.tool(annotations=WRITE, description=(
        "Confirme ou annule une activité « à confirmer » (foot, trail) ou l'inscription à un événement. "
        "confirmed → reste à faire ; ne marque JAMAIS une séance comme réalisée."))
    async def mark_event_confirmation(
        event_id: Annotated[str, Field(description="session_key (ex. 20261103_football) ou clé d'événement (ex. corps_nuds_night)")],
        confirmed_status: Literal["confirmed", "declined", "cancelled"],
        user_approval_reference: str,
        confirmed_time_local: HHMM | None = None,
        idempotency_key: str | None = None,
    ) -> dict[str, Any]:
        k = idempotency_key or _auto_key("mark_event_confirmation", {"e": event_id, "s": confirmed_status,
                                                                     "ap": user_approval_reference, "t": confirmed_time_local})
        return await rpc("mark_event_confirmation", p_ref_key=event_id, p_confirmed_status=confirmed_status,
                         p_user_approval_reference=user_approval_reference, p_idempotency_key=k,
                         p_confirmed_time_local=confirmed_time_local, p_actor="claude_mcp")

    @mcp.tool(annotations=WRITE, description=(
        "Enregistre une séance que l'utilisateur DÉCLARE avoir réalisée dans la conversation (ex. il colle ses S1/S2/S3). "
        "Uniquement des valeurs qu'il a données, rien d'estimé ; provenance user_report. L'échauffement n'est pas une série de travail."))
    async def record_user_reported_session(session: ReportedSession, user_approval_reference: str,
                                           idempotency_key: str | None = None) -> dict[str, Any]:
        if not user_approval_reference or len(user_approval_reference.strip()) < 3:
            raise ToolError(json.dumps({"success": False, "error": "approval_required", "synchronized": False}))
        payload = session.model_dump(mode="json", exclude_none=True)
        payload["notes"] = (payload.get("notes") or "") + f"\n[déclaré en conversation : « {user_approval_reference[:200]} »]"
        k = idempotency_key or _auto_key("record_user_reported_session", payload)
        return await rpc("complete_activity", p_payload=payload, p_idempotency_key=k, p_actor="claude_mcp")

    return mcp


def _d(v: dt.date | None) -> str | None:
    return v.isoformat() if v else None
