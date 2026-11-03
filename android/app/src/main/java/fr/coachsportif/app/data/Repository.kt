package fr.coachsportif.app.data

import fr.coachsportif.app.data.auth.Session
import fr.coachsportif.app.data.auth.SessionStore
import fr.coachsportif.app.data.local.CachedPayload
import fr.coachsportif.app.data.local.EntryDraft
import fr.coachsportif.app.data.local.LocalDb
import fr.coachsportif.app.data.local.OutboxOp
import fr.coachsportif.app.data.remote.ApiException
import fr.coachsportif.app.data.remote.AppJson
import fr.coachsportif.app.data.remote.OfflineException
import fr.coachsportif.app.data.remote.SupabaseApi
import fr.coachsportif.app.domain.CompletionPayload
import fr.coachsportif.app.domain.DayPlan
import fr.coachsportif.app.domain.ExerciseHistory
import fr.coachsportif.app.domain.RecentSessions
import fr.coachsportif.app.domain.VariantRow
import fr.coachsportif.app.domain.WeekPlan
import fr.coachsportif.app.domain.WeekSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

data class Cached<T>(val data: T, val fetchedAtMs: Long, val planVersion: Int?)

sealed interface SaveOutcome {
    /** Confirmé par le serveur (réponse success:true). */
    data class Synced(val completedActivityId: String?) : SaveOutcome
    /** Enregistré sur le téléphone, envoi automatique au retour du réseau. */
    data object Queued : SaveOutcome
    /** Refus métier (ex. séance déjà terminée) : rien n'a été écrit côté serveur. */
    data class Rejected(val code: String, val detail: String?) : SaveOutcome
}

/**
 * Point d'accès unique aux données. L'application ne décide d'aucune séance :
 * elle affiche ce que le coach a publié et enregistre ce que l'utilisateur a réalisé.
 */
class Repository(
    private val api: SupabaseApi,
    private val sessions: SessionStore,
    private val db: LocalDb,
    private val scheduleSync: () -> Unit,
) {
    private val _session = MutableStateFlow(sessions.load())
    val session: StateFlow<Session?> = _session.asStateFlow()
    private val tokenLock = Mutex()
    private val flushLock = Mutex()

    val isConfigured get() = api.isConfigured
    val outbox: Flow<List<OutboxOp>> = db.outbox().observeAll()

    // ------------------------------------------------------------ auth
    suspend fun signIn(email: String, password: String) {
        val s = api.signIn(email.trim(), password)
        sessions.save(s); _session.value = s
    }

    suspend fun signOut() {
        sessions.clear(); _session.value = null
        db.cache().clear(); db.drafts().clear(); db.outbox().clear()
    }

    private suspend fun token(forceRefresh: Boolean = false): String = tokenLock.withLock {
        val s = _session.value ?: throw ApiException(401, "not_authenticated", null)
        if (!forceRefresh && !s.expiresSoon()) return s.accessToken
        val fresh = try {
            api.refresh(s.refreshToken)
        } catch (e: ApiException) {
            sessions.clear(); _session.value = null; throw e
        }
        sessions.save(fresh); _session.value = fresh
        fresh.accessToken
    }

    suspend fun rpc(fn: String, args: JsonObject = JsonObject(emptyMap())): JsonElement = try {
        api.rpc(fn, args, token())
    } catch (e: ApiException) {
        if (e.status == 401) api.rpc(fn, args, token(forceRefresh = true)) else throw e
    }

    // ------------------------------------------------------------ lecture avec cache
    private fun <T> observe(key: String, ser: KSerializer<T>): Flow<Cached<T>?> =
        db.cache().observe(key).map { c -> c?.let { Cached(AppJson.decodeFromString(ser, it.json), it.fetchedAtMs, it.planVersion) } }

    private suspend fun <T> fetch(key: String, ser: KSerializer<T>, fn: String, args: JsonObject, version: (T) -> Int?): T {
        val el = rpc(fn, args)
        val data = AppJson.decodeFromJsonElement(ser, el)
        db.cache().put(CachedPayload(key, el.toString(), System.currentTimeMillis(), version(data)))
        return data
    }

    fun observeDay(date: String) = observe("day:$date", DayPlan.serializer())
    suspend fun refreshDay(date: String) =
        fetch("day:$date", DayPlan.serializer(), "get_day_plan", buildJsonObject { put("p_date", date) }) { it.planVersion }

    fun observeWeek(monday: String) = observe("week:$monday", WeekPlan.serializer())
    fun observeWeekSummary(monday: String) = observe("summary:$monday", WeekSummary.serializer())
    suspend fun refreshWeek(monday: String) {
        fetch("week:$monday", WeekPlan.serializer(), "list_week_plan", buildJsonObject { put("p_week_start", monday) }) { it.planVersion }
        fetch("summary:$monday", WeekSummary.serializer(), "get_week_summary", buildJsonObject { put("p_week_start", monday) }) { null }
    }

    suspend fun recentSessions(limit: Int = 30): RecentSessions =
        AppJson.decodeFromJsonElement(RecentSessions.serializer(),
            rpc("get_recent_completed_sessions", buildJsonObject { put("p_limit", limit) }))

    suspend fun exerciseHistory(variantKey: String): ExerciseHistory =
        AppJson.decodeFromJsonElement(ExerciseHistory.serializer(),
            rpc("get_exercise_history", buildJsonObject { put("p_variant_key", variantKey); put("p_limit", 30) }))

    suspend fun variants(): List<VariantRow> =
        AppJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(VariantRow.serializer()),
            api.select("exercise_variants?select=key,name,equipment_variant,split&order=split,name", token()))

    // ------------------------------------------------------------ écriture : file d'attente idempotente
    suspend fun complete(payload: CompletionPayload, label: String): SaveOutcome {
        val key = "android-" + UUID.randomUUID()
        val args = buildJsonObject {
            put("p_payload", AppJson.encodeToJsonElement(payload))
            put("p_idempotency_key", key)
            put("p_actor", "android_app")
        }
        db.outbox().enqueue(OutboxOp(key, "complete_activity", args.toString(), label, System.currentTimeMillis()))
        val outcome = flushOne(key)
        if (outcome is SaveOutcome.Synced) payload.plannedSessionKey?.let { db.drafts().remove(it) }
        return outcome
    }

    /**
     * « Pas fait / pas dispo » : la séance passe en « skipped », avec le motif. L'ancienne version reste
     * dans l'historique. Passe par la même file d'attente que les résultats (fonctionne hors connexion).
     */
    suspend fun skip(sessionKey: String, version: Int, date: String, reason: String, label: String): SaveOutcome {
        val key = "android-" + UUID.randomUUID()
        val args = buildJsonObject {
            put("p_session_key", sessionKey)
            put("p_expected_version", version)
            put("p_patch", buildJsonObject { put("status", "skipped"); put("notes", "Pas fait : $reason") })
            put("p_reason", "Signalé dans l'application : $reason")
            put("p_user_approval_reference", "Déclaré par l'utilisateur dans l'application : $reason")
            put("p_idempotency_key", key)
            put("p_actor", "android_app")
        }
        db.outbox().enqueue(OutboxOp(key, "revise_session", args.toString(), label, System.currentTimeMillis()))
        val outcome = flushOne(key)
        if (outcome is SaveOutcome.Synced) runCatching { refreshDay(date) }
        return outcome
    }

    /** Envoie toute la file (appelé par SyncWorker et au démarrage). Retourne true si la file est vide. */
    suspend fun flush(): Boolean = flushLock.withLock {
        for (op in db.outbox().pending()) {
            if (sendOp(op) is SaveOutcome.Queued) return false
        }
        true
    }

    private suspend fun flushOne(key: String): SaveOutcome = flushLock.withLock {
        val op = db.outbox().pending().firstOrNull { it.idempotencyKey == key } ?: return SaveOutcome.Queued
        sendOp(op)
    }

    private suspend fun sendOp(op: OutboxOp): SaveOutcome = try {
        val res = rpc(op.fn, AppJson.parseToJsonElement(op.argsJson) as JsonObject) as JsonObject
        db.outbox().remove(op.idempotencyKey)
        val payload = (AppJson.parseToJsonElement(op.argsJson) as JsonObject)["p_payload"] as? JsonObject
        (payload?.get("date"))?.let { d -> runCatching { refreshDay(d.toString().trim('"')) } }
        SaveOutcome.Synced(res["completed_activity_id"]?.toString()?.trim('"'))
    } catch (e: OfflineException) {
        db.outbox().markAttempt(op.idempotencyKey, "hors ligne", "pending"); scheduleSync(); SaveOutcome.Queued
    } catch (e: ApiException) {
        if (e.status >= 500 || e.status == 429) {
            db.outbox().markAttempt(op.idempotencyKey, e.code, "pending"); scheduleSync(); SaveOutcome.Queued
        } else {
            db.outbox().markAttempt(op.idempotencyKey, e.code, "failed"); SaveOutcome.Rejected(e.code, e.detail)
        }
    }

    suspend fun discardFailed(key: String) = db.outbox().remove(key)

    // ------------------------------------------------------------ brouillons de saisie
    suspend fun loadDraft(sessionKey: String): String? = db.drafts().get(sessionKey)?.json
    suspend fun saveDraft(sessionKey: String, json: String) =
        db.drafts().put(EntryDraft(sessionKey, json, System.currentTimeMillis()))

    // ------------------------------------------------------------ RGPD
    suspend fun exportAll(): String = rpc("export_my_data").toString()
    suspend fun deleteAllData(confirmation: String) {
        rpc("delete_my_data", buildJsonObject { put("p_confirmation", confirmation) })
        db.cache().clear(); db.drafts().clear(); db.outbox().clear()
    }
}
