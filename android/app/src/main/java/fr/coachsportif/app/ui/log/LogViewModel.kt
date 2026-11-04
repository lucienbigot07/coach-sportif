package fr.coachsportif.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.SaveOutcome
import fr.coachsportif.app.domain.CompletionPayload
import fr.coachsportif.app.domain.EnduranceIn
import fr.coachsportif.app.domain.ExerciseRef
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.NewVariantIn
import fr.coachsportif.app.domain.VariantRow
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.domain.SetIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.time.Instant

@Serializable
data class SetDraft(
    val isWarmup: Boolean = false,
    val load: String = "",
    val perf: String = "",        // répétitions, PAS ou secondes selon la variante
    val rir: String = "",
    val done: Boolean = false,
)

/** Machine / variante possible pour un exercice (proposées par le coach dans target.alternatives). */
@Serializable
data class VariantOption(val key: String, val label: String, val goal: String? = null)

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
    val added: Boolean = false,           // ajouté pendant la séance, hors programme du coach
    val newVariant: NewVariantIn? = null, // machine jamais enregistrée : créée à l'envoi
    val options: List<VariantOption> = emptyList(), // choix barre / machine…, vide si une seule possibilité
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
    /** Catalogue des machines/variantes connues (chargé à la demande pour « Ajouter un exercice »). */
    val catalog = MutableStateFlow<List<VariantRow>?>(null)
    val catalogError = MutableStateFlow<String?>(null)

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
        val alts = a.target["alternatives"] as? JsonObject
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
            ).let { ex ->
                val extra = (alts?.get(e.variantKey) as? JsonArray)?.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val k = o["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    VariantOption(k, o["label"]?.jsonPrimitive?.contentOrNull ?: k, o["goal"]?.jsonPrimitive?.contentOrNull)
                }.orEmpty()
                if (extra.isEmpty()) ex
                else ex.copy(options = listOf(VariantOption(e.variantKey, e.equipmentVariant?.let { "${e.name} ($it)" } ?: e.name,
                    ex.targetLine)) + extra)
            }
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

    fun loadCatalog() = viewModelScope.launch {
        if (catalog.value != null) return@launch
        runCatching { repo.variants() }
            .onSuccess { catalog.value = it; catalogError.value = null }
            .onFailure { catalogError.value = "Liste des machines indisponible hors connexion. Tu peux créer l'exercice à la main." }
    }

    /** Ajoute une machine déjà connue, avec sa dernière référence si le réseau le permet. */
    fun addKnownExercise(v: VariantRow) {
        val ex = ExDraft(variantKey = v.key, name = v.name, equipment = v.equipmentVariant, added = true,
            sets = List(3) { SetDraft() })
        edit { d -> d.copy(exercises = d.exercises + ex) }
        viewModelScope.launch {
            val h = runCatching { repo.exerciseHistory(v.key) }.getOrNull() ?: return@launch
            val ll = h.reference?.lastLogged
            val load = ll?.sets?.firstOrNull()?.loadKg
            edit { d -> d.copy(exercises = d.exercises.map { e ->
                if (e.variantKey != v.key || !e.added) e else e.copy(
                    loadBasis = h.variant.loadBasis, unit = h.variant.performanceUnit,
                    refLine = ll?.let { "Dernière (${it.date}) : ${Fmt.sets(it.sets, h.variant.loadBasis)}" }
                        ?: Fmt.historical(h.reference?.historicalReference)?.let { "Référence : $it" },
                    sets = if (e.sets.none { it.done || it.load.isNotEmpty() }) List(e.sets.size) { SetDraft(load = load?.let { Fmt.num(it) } ?: "") } else e.sets,
                )
            }) }
        }
    }

    /** Crée un exercice sur une machine jamais enregistrée : ses charges ne seront comparées qu'à elle-même. */
    fun addNewExercise(name: String, equipment: String, unit: String, loadBasis: String, split: String?) {
        val slug = Normalizer.normalize("$name $equipment", Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40)
        val key = (slug.ifEmpty { "exercice" }) + "_" + System.currentTimeMillis().toString(36).takeLast(4)
        val nv = NewVariantIn(key = key, name = name.trim(), split = split, equipmentVariant = equipment.trim().ifEmpty { null },
            loadBasis = loadBasis, performanceUnit = unit)
        edit { d -> d.copy(exercises = d.exercises + ExDraft(variantKey = key, name = nv.name, equipment = nv.equipmentVariant,
            loadBasis = loadBasis, unit = unit, added = true, newVariant = nv, refLine = "Nouvelle machine : pas encore de référence",
            sets = List(3) { SetDraft() })) }
    }

    /**
     * Change la machine / variante d'un exercice (ex. développé couché barre → machine) :
     * référence, objectif, unité et charges pré-remplies suivent la variante choisie.
     * Les séries déjà saisies sont conservées et seront enregistrées sur la nouvelle variante.
     */
    fun switchVariant(ex: Int, opt: VariantOption) {
        val cur = (ui.value as? LogUi.Ready)?.draft?.exercises?.getOrNull(ex) ?: return
        if (cur.variantKey == opt.key) return
        val goal = opt.goal?.let { if (it.startsWith("🎯")) it else "🎯 $it" }
        edit { d -> d.copy(exercises = d.exercises.mapIndexed { i, e ->
            if (i != ex) e else e.copy(variantKey = opt.key, name = opt.label, equipment = null, targetLine = goal,
                refLine = "Chargement de la référence…")
        }) }
        viewModelScope.launch {
            val h = runCatching { repo.exerciseHistory(opt.key) }.getOrNull()
            edit { d -> d.copy(exercises = d.exercises.mapIndexed { i, e ->
                if (i != ex || e.variantKey != opt.key) e
                else if (h == null) e.copy(refLine = "Référence indisponible hors connexion")
                else {
                    val ll = h.reference?.lastLogged
                    val load = ll?.sets?.firstOrNull()?.loadKg ?: Fmt.historicalFirstLoad(h.reference?.historicalReference)
                    val untouched = e.sets.none { it.done || it.perf.isNotEmpty() }
                    e.copy(
                        name = h.variant.name, equipment = h.variant.equipmentVariant, loadBasis = h.variant.loadBasis,
                        unit = h.variant.performanceUnit,
                        refLine = ll?.let { "Dernière (${it.date}) : ${Fmt.sets(it.sets, h.variant.loadBasis)}" }
                            ?: Fmt.historical(h.reference?.historicalReference)?.let { "Référence : $it${Fmt.loadBasisLabel(h.variant.loadBasis)}" }
                            ?: "Pas encore de référence sur cette machine",
                        sets = if (untouched) e.sets.map { s -> s.copy(load = load?.let { Fmt.num(it) } ?: "") } else e.sets,
                    )
                }
            }) }
        }
    }

    /** Seuls les exercices ajoutés pendant la séance peuvent être retirés ; le programme du coach reste intact. */
    fun removeAdded(ex: Int) = edit { d -> d.copy(exercises = d.exercises.filterIndexed { i, e -> !(i == ex && e.added) }) }

    fun editComment(ex: Int, text: String) = edit { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e -> if (i == ex) e.copy(comment = text.take(500)) else e })
    }

    /** Copie la charge de la série précédente (geste rapide). */
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
                    variantKey = e.variantKey, newVariant = e.newVariant, exercisePosition = i + 1, setIndex = idx, isWarmup = s.isWarmup,
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
        // écart au programme, signalé explicitement au coach
        val addedDone = d.exercises.filter { e -> e.added && e.sets.any { it.done && it.perf.toIntOrNull() != null } }
        val deviation = addedDone.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Ajouté hors programme : ") { e ->
            e.name + (e.equipment?.let { " ($it)" } ?: "") }
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
            notes = (listOfNotNull(d.notes.trim().ifEmpty { null }, deviation) + orphanComments).joinToString("\n").ifBlank { null },
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

    /** Message prérempli pour le coach : la séance en cours n'est pas encore en base, on la résume ici. */
    fun coachMessage(): String {
        val r = ui.value as? LogUi.Ready ?: return "Question sur ma séance : "
        val a = r.activity; val d = r.draft
        val lines = mutableListOf("Je suis en pleine séance « ${a.title} » du ${Fmt.dayTitle(a.date)} (${a.sessionKey}).")
        val done = d.exercises.mapNotNull { e ->
            val s = e.sets.filter { it.done && !it.isWarmup && it.perf.isNotBlank() }
            if (s.isEmpty() && e.comment.isBlank()) null
            else "- ${e.name}${e.equipment?.let { " ($it)" } ?: ""}${if (e.added) " [ajouté hors programme]" else ""} : " +
                s.joinToString(" / ") { (if (it.load.isNotBlank()) "${it.load} kg × " else "") + it.perf + (if (e.unit == "steps") " pas" else "") } +
                (e.comment.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
        }
        if (done.isNotEmpty()) { lines += "Déjà fait (pas encore enregistré) :"; lines += done }
        else if (a.sport == "strength") lines += "Je n'ai encore validé aucune série."
        val left = d.exercises.filter { e -> e.sets.none { it.done } }.map { it.name }
        if (left.isNotEmpty()) lines += "Reste : " + left.joinToString(", ")
        lines += ""
        lines += "Ma question : "
        return lines.joinToString("\n")
    }

    fun workSetsReady(d: LogDraft) = d.exercises.sumOf { e -> e.sets.count { it.done && !it.isWarmup && it.perf.toIntOrNull() != null } }
    fun clearOutcome() = outcome.update { null }
}
