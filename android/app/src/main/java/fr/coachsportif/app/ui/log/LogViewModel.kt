package fr.coachsportif.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.SaveOutcome
import fr.coachsportif.app.domain.CompletionPayload
import fr.coachsportif.app.domain.EnduranceIn
import fr.coachsportif.app.domain.ExerciseRef
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.domain.SetIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class SetDraft(
    val isWarmup: Boolean = false,
    val load: String = "",
    val perf: String = "",        // répétitions, PAS ou secondes selon la variante
    val rir: String = "",
    val done: Boolean = false,
)

@Serializable
data class ExDraft(
    val variantKey: String,
    val name: String,
    val equipment: String? = null,
    val loadBasis: String? = null,
    val unit: String = "reps",
    val priority: Boolean = false,
    val refLine: String? = null,
    val targetLine: String? = null,
    val sets: List<SetDraft> = emptyList(),
    val comment: String = "",             // commentaire libre sur l'exercice (sensations, réglage machine…)
)

@Serializable
data class EnduranceDraft(
    val distance: String = "",    // km (course) ou m (natation)
    val duration: String = "",    // mm:ss ou h:mm:ss
    val ascent: String = "",
    val avgHr: String = "",
    val maxHr: String = "",
    val effortType: String? = null,
    val surface: String = "",
    val poolLength: String = "",
)

@Serializable
data class LogDraft(
    val activityVersion: Int? = null,     // version de la séance publiée par le coach au moment du brouillon
    val exercises: List<ExDraft> = emptyList(),
    val endurance: EnduranceDraft = EnduranceDraft(),
    val rpe: Int? = null,
    val notes: String = "",
    val startedAtMs: Long = System.currentTimeMillis(),
)

sealed interface LogUi {
    data object Loading : LogUi
    data class Ready(val activity: PlannedActivity, val draft: LogDraft) : LogUi
    data class Missing(val reason: String) : LogUi
}

class LogViewModel(private val repo: Repository, private val date: String, private val sessionKey: String) : ViewModel() {
    val ui = MutableStateFlow<LogUi>(LogUi.Loading)
    val saving = MutableStateFlow(false)
    val outcome = MutableStateFlow<SaveOutcome?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            // dernière version publiée par le coach (le cache sert seulement hors connexion)
            val fresh = runCatching { repo.refreshDay(date) }.getOrNull()
            val a = (fresh ?: repo.observeDay(date).first()?.data)?.activities?.firstOrNull { it.sessionKey == sessionKey }
            if (a == null) { ui.value = LogUi.Missing("Séance introuvable dans le dernier planning synchronisé."); return@launch }
            val saved = repo.loadDraft(sessionKey)?.let { runCatching { json.decodeFromString(LogDraft.serializer(), it) }.getOrNull() }
            // le coach a modifié la séance depuis le brouillon : on repart de la nouvelle version
            // tant qu'aucune série n'a été cochée (sinon on garde la saisie en cours)
            val untouched = saved?.exercises?.none { e -> e.sets.any { it.done } } ?: true
            val draft = if (saved == null || (saved.activityVersion != a.version && untouched)) initialDraft(a) else saved
            ui.value = LogUi.Ready(a, draft)
        }
    }

    private fun initialDraft(a: PlannedActivity): LogDraft {
        val refs: List<ExerciseRef> = a.exercises.ifEmpty { a.splitReferences }
        val nSets = (a.target["sets_per_exercise_approx"]?.toString()?.toIntOrNull()) ?: 3
        return LogDraft(activityVersion = a.version, exercises = refs.map { e ->
            val ll = e.reference?.lastLogged
            val last = ll?.sets
            val load = e.targetLoadKg ?: last?.firstOrNull()?.loadKg
            ExDraft(
                variantKey = e.variantKey, name = e.name, equipment = e.equipmentVariant, loadBasis = e.loadBasis,
                unit = e.performanceUnit, priority = e.isPriority,
                refLine = ll?.let { "Dernière (${it.date}) : ${Fmt.sets(it.sets, e.loadBasis)}" }
                    ?: Fmt.historical(e.reference?.historicalReference)?.let { "Référence : $it${Fmt.loadBasisLabel(e.loadBasis)}" },
                // objectif fixé par le coach pour CETTE séance, sinon objectif de fond non validé
                targetLine = e.notes?.takeIf { e.targetSets != null }?.let { "🎯 $it" }
                    ?: Fmt.futureTarget(e.reference?.futureTargetNotValidated),
                sets = List(e.targetSets ?: nSets) { SetDraft(load = load?.let { Fmt.num(it) } ?: "") },
            )
        })
    }

    fun edit(transform: (LogDraft) -> LogDraft) {
        val cur = ui.value as? LogUi.Ready ?: return
        val next = transform(cur.draft)
        ui.value = cur.copy(draft = next)
        viewModelScope.launch { repo.saveDraft(sessionKey, json.encodeToString(LogDraft.serializer(), next)) }
    }

    fun editSet(ex: Int, set: Int, f: (SetDraft) -> SetDraft) = edit { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e ->
            if (i != ex) e else e.copy(sets = e.sets.mapIndexed { j, s -> if (j == set) f(s) else s })
        })
    }

    fun addSet(ex: Int, warmup: Boolean) = edit { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e ->
            if (i != ex) e else {
                val prev = e.sets.lastOrNull { it.isWarmup == warmup } ?: e.sets.lastOrNull()
                val new = SetDraft(isWarmup = warmup, load = prev?.load ?: "")
                if (warmup) e.copy(sets = listOf(new) + e.sets) else e.copy(sets = e.sets + new)
            }
        })
    }

    /** Copie la charge de la série précédente (geste rapide). */
    fun editComment(ex: Int, text: String) = edit { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e -> if (i == ex) e.copy(comment = text.take(500)) else e })
    }

    fun copyPreviousLoad(ex: Int, set: Int) = edit { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e ->
            if (i != ex || set == 0) e else e.copy(sets = e.sets.mapIndexed { j, s -> if (j == set) s.copy(load = e.sets[set - 1].load) else s })
        })
    }

    /** Construit le payload : seules les séries cochées ET mesurées sont envoyées ; inconnu = null, jamais 0. */
    fun buildPayload(a: PlannedActivity, d: LogDraft): CompletionPayload {
        val sets = mutableListOf<SetIn>()
        val orphanComments = mutableListOf<String>()
        d.exercises.forEachIndexed { i, e ->
            var work = 0; var warm = 0
            val before = sets.size
            e.sets.filter { it.done }.forEach { s ->
                val perf = s.perf.trim().toIntOrNull() ?: return@forEach
                val idx = if (s.isWarmup) ++warm else ++work
                sets += SetIn(
                    variantKey = e.variantKey, exercisePosition = i + 1, setIndex = idx, isWarmup = s.isWarmup,
                    loadKg = Fmt.parseDecimal(s.load),
                    reps = perf.takeIf { e.unit == "reps" || e.unit == "round_trips" },
                    steps = perf.takeIf { e.unit == "steps" },
                    seconds = perf.takeIf { e.unit == "seconds" },
                    rir = s.rir.trim().toIntOrNull(),
                )
            }
            // le commentaire de l'exercice est rattaché à sa dernière série de travail ;
            // sans série enregistrée, il rejoint les notes de la séance (rien n'est perdu)
            val comment = e.comment.trim()
            if (comment.isNotEmpty()) {
                val lastWork = (sets.size - 1 downTo before).firstOrNull { !sets[it].isWarmup }
                if (lastWork != null) sets[lastWork] = sets[lastWork].copy(notes = comment)
                else orphanComments += "${e.name} : $comment"
            }
        }
        val en = d.endurance
        val distM = Fmt.parseDecimal(en.distance)?.let { if (a.sport == "swimming") it else it * 1000 }
        val endurance = if (a.sport == "strength") null else EnduranceIn(
            distanceM = distM, durationS = Fmt.parseDuration(en.duration), ascentM = en.ascent.toIntOrNull(),
            avgHr = en.avgHr.toIntOrNull(), maxHr = en.maxHr.toIntOrNull(), effortType = en.effortType,
            surface = en.surface.ifBlank { null }, poolLengthM = en.poolLength.toIntOrNull(),
        ).takeIf { it != EnduranceIn() }
        return CompletionPayload(
            plannedSessionKey = a.sessionKey, date = a.date, sport = a.sport, subtype = a.subtype,
            rpe10 = d.rpe?.toDouble(), actualSessionType = en.effortType,
            notes = (listOf(d.notes.trim()).filter { it.isNotEmpty() } + orphanComments).joinToString("\n").ifBlank { null },
            startedAt = Instant.ofEpochMilli(d.startedAtMs).toString(), endedAt = Instant.now().toString(),
            sets = sets, endurance = endurance,
        )
    }

    fun finish() {
        val cur = ui.value as? LogUi.Ready ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val label = "${cur.activity.title} du ${cur.activity.date}"
            outcome.value = repo.complete(buildPayload(cur.activity, cur.draft), label)
            saving.value = false
        }
    }

    fun workSetsReady(d: LogDraft) = d.exercises.sumOf { e -> e.sets.count { it.done && !it.isWarmup && it.perf.toIntOrNull() != null } }
    fun clearOutcome() = outcome.update { null }
}
