package fr.coachsportif.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Formatage français, sans jamais transformer une valeur inconnue en zéro. */
object Fmt {
    val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    private val FR = Locale.FRENCH

    fun today(): LocalDate = LocalDate.now(PARIS)
    fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun dayTitle(iso: String): String {
        val d = LocalDate.parse(iso)
        val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, FR).replaceFirstChar { it.uppercase() }
        return "$dow ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, FR)}"
    }

    fun weekTitle(start: String, end: String): String {
        val s = LocalDate.parse(start); val e = LocalDate.parse(end)
        val f = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
        return "📅 Ton planning sportif — du ${s.format(f)} au ${e.format(f)}"
    }

    fun num(v: Double?): String = when {
        v == null -> "?"
        v == v.roundToInt().toDouble() -> v.roundToInt().toString()
        else -> String.format(FR, "%.1f", v)
    }

    fun pace(sPerKm: Double?): String? = sPerKm?.let {
        val s = it.roundToInt(); "%d:%02d/km".format(s / 60, s % 60)
    }

    fun duration(s: Int?): String? = s?.let {
        val h = it / 3600; val m = (it % 3600) / 60; val sec = it % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun km(m: Double?): String? = m?.let { if (it >= 1000) "${num(it / 1000)} km" else "${num(it)} m" }

    fun sportEmoji(sport: String, subtype: String? = null): String = when (sport) {
        "running" -> if (subtype == "trail") "⛰️" else "🏃"
        "swimming" -> "🏊"
        "strength" -> "💪"
        "football" -> "⚽"
        "hyrox" -> "🛷"
        "cycling" -> "🚴"
        "walking" -> "🚶"
        "mobility" -> "🧘"
        else -> "🏅"
    }

    fun statusLabel(status: String): String = when (status) {
        "proposed" -> "Proposé"
        "planned" -> "À faire"
        "pending_confirmation" -> "À confirmer"
        "in_progress" -> "En cours"
        "completed" -> "Terminé"
        "cancelled" -> "Annulé"
        "skipped" -> "Ignoré"
        else -> status
    }

    fun splitLabel(split: String?): String? = when (split) {
        "dos_biceps" -> "Dos / Biceps"
        "quadriceps" -> "Quadriceps"
        "pecs_triceps" -> "Pecs / Triceps"
        "ischios_fessiers" -> "Ischios / Fessiers"
        else -> split
    }

    fun loadBasisLabel(b: String?): String = when (b) {
        "per_hand" -> " par main"
        "total" -> " (total)"
        "machine" -> " (machine)"
        else -> ""
    }

    /** « 44 kg × 32 pas / 44 × 33 / 44 × 33 » — l'unité de performance de la variante est respectée. */
    fun sets(sets: List<SetOut>, basis: String? = null): String =
        if (sets.isEmpty()) "—" else sets.joinToString(" / ") { s ->
            val perf = when {
                s.steps != null -> "${s.steps} pas"
                s.seconds != null -> "${s.seconds} s"
                s.reps != null -> "${s.reps}"
                s.distanceM != null -> "${num(s.distanceM)} m"
                else -> "?"
            }
            if (s.loadKg != null) "${num(s.loadKg)} kg × $perf" else perf
        } + loadBasisLabel(basis ?: sets.firstOrNull()?.loadBasis)

    /** Formate une référence historique, quelle que soit sa forme dans le dossier d'origine. */
    fun historical(ref: JsonObject?): String? {
        val r = ref?.get("reference") as? JsonObject ?: return null
        fun pair(e: JsonElement): String = when (e) {
            is JsonArray -> "${num(e[0].jsonPrimitive.doubleOrNull)} × ${e[1].jsonPrimitive.intOrNull ?: "?"}"
            is JsonObject -> buildString {
                append(num((e["load_kg"] as? JsonPrimitive)?.doubleOrNull)); append(" kg × ")
                append((e["steps"] as? JsonPrimitive)?.intOrNull?.let { "$it pas" }
                    ?: (e["reps"] as? JsonPrimitive)?.intOrNull?.toString() ?: "?")
            }
            else -> e.toString()
        }
        r["sets"]?.let { return it.jsonArray.joinToString(" / ") { e -> pair(e) } }
        r["working_sets"]?.let { ws ->
            val warm = r["warmup_set"]?.let { "échauffement ${pair(it)} ; " } ?: ""
            return warm + ws.jsonArray.joinToString(" / ") { e -> pair(e) } +
                (if (r["third_working_set"] is JsonPrimitive && (r["third_working_set"] as JsonPrimitive).content == "null") " (3ᵉ série non faite)" else "")
        }
        r["last_session_sets"]?.let { last ->
            val best = r["best_full_sets"]?.jsonArray?.joinToString(" / ") { pair(it) }
            return "dernière : " + last.jsonArray.joinToString(" / ") { pair(it) } + (best?.let { " · meilleure : $it" } ?: "")
        }
        r["best_full_sets_reps"]?.let { return "meilleure : " + it.jsonArray.joinToString(" / ") { e -> e.jsonPrimitive.content } }
        r["best_observed_set"]?.let { return "meilleur essai isolé : ${pair(it)} (3 séries non prouvées)" }
        r["sets_seconds"]?.let { return it.jsonArray.joinToString(" / ") { e -> "${e.jsonPrimitive.content} s" } }
        r["load_kg"]?.let { return "${num(it.jsonPrimitive.doubleOrNull)} kg × ${r["round_trips"]?.jsonPrimitive?.content ?: "?"} A/R" }
        return r.toString()
    }

    /** Objectif NON validé, affiché comme tel. */
    fun futureTarget(t: JsonObject?): String? {
        if (t == null) return null
        fun g(k: String) = (t[k] as? JsonPrimitive)?.content
        val load = g("load_kg") ?: g("load_kg_per_hand")
        val perf = g("reps_each")?.let { "$it" } ?: g("steps_each")?.let { "$it pas" }
        return when {
            g("try_next_machine_increment") == "true" -> "🎯 Essayer le cran supérieur (même machine)"
            perf != null -> "🎯 Visé : ${load?.let { "$it kg" } ?: ""}${if (g("load_kg_per_hand") != null) " par main" else ""} × $perf × ${g("sets") ?: 3} (pas encore validé)"
            else -> null
        }
    }

    /** Consignes lisibles à partir de la prescription JSON. */
    fun target(t: JsonObject): List<String> {
        fun arr(k: String) = (t[k] as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }
        fun p(k: String) = (t[k] as? JsonPrimitive)
        val out = mutableListOf<String>()
        p("distance_m")?.doubleOrNull?.let { out += "Distance : ${km(it)}" }
        arr("distance_m_range")?.let { out += "Distance : ${num(it[0])}–${num(it[1])} m" }
        arr("duration_min_range")?.let { out += "Durée : ${num(it[0])}–${num(it[1])} min" }
        arr("pace_s_per_km_range")?.let { out += "Allure : ${pace(it[0])?.removeSuffix("/km")}–${pace(it[1])}" }
        arr("rpe_10_range")?.let { out += "Ressenti : ${num(it[0])}–${num(it[1])}/10" }
        arr("rir_range")?.let { out += "Réserve : ${num(it[0])}–${num(it[1])} répétitions" }
        arr("if_tired_distance_m_range")?.let { out += "Si fatigué : ${km(it[0])}–${km(it[1])}" }
        p("intensity")?.content?.let { out += "Intensité : $it" }
        p("effort")?.content?.let { out += "Effort : $it" }
        p("load")?.content?.let { out += "Charges : $it" }
        p("sets_per_exercise_approx")?.intOrNull?.let { out += "≈ $it séries par exercice" }
        (t["avoid"] as? JsonArray)?.let { a -> out += "À éviter : " + a.joinToString(", ") { it.jsonPrimitive.content } }
        return out
    }

    fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** « 24:02 » ou « 1:02:30 » → secondes ; null si illisible. */
    fun parseDuration(s: String): Int? {
        val parts = s.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }
}
