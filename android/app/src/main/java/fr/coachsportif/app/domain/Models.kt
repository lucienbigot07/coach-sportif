package fr.coachsportif.app.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Miroir des réponses JSON des fonctions RPC (db/migrations/0003_functions.sql).
 * Noms en camelCase ; conversion snake_case par JsonNamingStrategy.SnakeCase (voir Json.kt).
 * Toute valeur inconnue reste null : jamais de 0 par défaut pour une charge, un chrono ou une FC.
 */

@Serializable
data class DayPlan(
    val date: String,
    val timezone: String? = null,
    val planId: String? = null,
    val planVersion: Int? = null,
    val activities: List<PlannedActivity> = emptyList(),
    val unplannedCompleted: List<CompletedActivity> = emptyList(),
    val message: String? = null,
)

@Serializable
data class PlannedActivity(
    val sessionKey: String,
    val id: String,
    val planId: String? = null,
    val date: String,
    val sport: String,
    val subtype: String? = null,
    val title: String,
    val sortOrder: Int? = null,
    val status: String,
    val confirmationRequired: Boolean = false,
    val confirmedTimeLocal: String? = null,
    val provisionalTimeLocal: String? = null,
    val target: JsonObject = JsonObject(emptyMap()),
    val notes: String? = null,
    val version: Int,
    val updatedBy: String? = null,
    val updatedAt: String? = null,
    val exercises: List<ExerciseRef> = emptyList(),
    val splitReferences: List<ExerciseRef> = emptyList(),
    val result: CompletedActivity? = null,
    val lastChange: LastChange? = null,
)

@Serializable
data class LastChange(
    val at: String,
    val actor: String,
    val action: String,
    val reason: String? = null,
    val previous: JsonObject? = null,
)

@Serializable
data class ExerciseRef(
    val variantKey: String,
    val name: String,
    val split: String? = null,
    val equipmentVariant: String? = null,
    val location: String? = null,
    val loadBasis: String? = null,
    val performanceUnit: String = "reps",
    val notes: String? = null,
    val position: Int? = null,
    val isPriority: Boolean = false,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetSteps: Int? = null,
    val targetSeconds: Int? = null,
    val targetLoadKg: Double? = null,
    val rirMin: Int? = null,
    val rirMax: Int? = null,
    val reference: VariantReference? = null,
)

@Serializable
data class VariantReference(
    val lastLogged: LastLogged? = null,
    val historicalReference: JsonObject? = null,
    val futureTargetNotValidated: JsonObject? = null,
)

@Serializable
data class LastLogged(val date: String, val source: String? = null, val sets: List<SetOut> = emptyList())

@Serializable
data class SetOut(
    val setIndex: Int,
    val loadKg: Double? = null,
    val loadBasis: String? = null,
    val reps: Int? = null,
    val steps: Int? = null,
    val seconds: Int? = null,
    val distanceM: Double? = null,
    val rir: Int? = null,
    val notes: String? = null,
)

@Serializable
data class CompletedActivity(
    val completedActivityId: String,
    val key: String? = null,
    val date: String,
    val sport: String,
    val subtype: String? = null,
    @SerialName("rpe_10") val rpe10: Double? = null,
    val rpeRangeReported: List<Double>? = null,
    val plannedSessionType: String? = null,
    val actualSessionType: String? = null,
    val notes: String? = null,
    val source: String,
    val version: Int,
    val plannedSessionKey: String? = null,
    val endurance: Endurance? = null,
    val exercises: List<CompletedExercise> = emptyList(),
    val workSetsCount: Int? = null,
)

@Serializable
data class Endurance(
    val distanceM: Double? = null,
    val durationS: Int? = null,
    val paceSPerKm: Double? = null,
    val paceIsReportedOnly: Boolean? = null,
    val ascentM: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val effortType: String? = null,
    val surface: String? = null,
    val poolLengthM: Int? = null,
)

@Serializable
data class CompletedExercise(
    val variantKey: String,
    val name: String,
    val equipmentVariant: String? = null,
    val loadBasis: String? = null,
    val workSets: List<SetOut> = emptyList(),
    val warmupSets: List<SetOut> = emptyList(),
)

@Serializable
data class WeekPlan(
    val weekStart: String,
    val weekEnd: String,
    val planId: String? = null,
    val planVersion: Int? = null,
    val rationale: String? = null,
    val decisionNote: String? = null,
    val validatedAt: String? = null,
    val days: List<WeekDay> = emptyList(),
    val events: List<EventOut> = emptyList(),
)

@Serializable
data class WeekDay(
    val date: String,
    val isodow: Int,
    val activities: List<PlannedActivity> = emptyList(),
    val unplannedCompleted: List<CompletedActivity> = emptyList(),
)

@Serializable
data class EventOut(
    val key: String,
    val name: String,
    val eventDate: String? = null,
    val sport: String? = null,
    val distanceM: Int? = null,
    val registrationStatus: String,
)

@Serializable
data class WeekSummary(val weekStart: String, val planned: JsonObject? = null, val actual: JsonObject? = null)

@Serializable
data class ExerciseHistory(
    val variant: ExerciseRef,
    val reference: VariantReference? = null,
    val sameNameOtherVariants: List<String> = emptyList(),
    val sessions: List<HistorySession> = emptyList(),
)

@Serializable
data class HistorySession(
    val date: String,
    val completedActivityId: String,
    val source: String? = null,
    @SerialName("session_rpe_10") val sessionRpe10: Double? = null,
    val plannedSessionKey: String? = null,
    val workSets: List<SetOut> = emptyList(),
    val warmupSetsCount: Int? = null,
)

@Serializable
data class RecentSessions(val sessions: List<CompletedActivity> = emptyList())

@Serializable
data class VariantRow(val key: String, val name: String, val equipmentVariant: String? = null, val split: String? = null)

@Serializable
data class WriteResult(
    val success: Boolean,
    val completedActivityId: String? = null,
    val plannedSessionKey: String? = null,
    val version: Int? = null,
    val replayed: Boolean? = null,
)

/* ---------------------------------------------------------------- saisie (payload complete_activity) */

@Serializable
data class SetIn(
    val variantKey: String,
    val exercisePosition: Int? = null,
    val setIndex: Int,
    val isWarmup: Boolean = false,
    val loadKg: Double? = null,
    val reps: Int? = null,
    val steps: Int? = null,
    val seconds: Int? = null,
    val rir: Int? = null,
    val notes: String? = null,
)

@Serializable
data class EnduranceIn(
    val distanceM: Double? = null,
    val durationS: Int? = null,
    val ascentM: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val effortType: String? = null,
    val surface: String? = null,
    val poolLengthM: Int? = null,
)

@Serializable
data class CompletionPayload(
    val plannedSessionKey: String? = null,
    val date: String,
    val sport: String,
    val subtype: String? = null,
    @SerialName("rpe_10") val rpe10: Double? = null,
    val actualSessionType: String? = null,
    val notes: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val sets: List<SetIn> = emptyList(),
    val endurance: EnduranceIn? = null,
)
