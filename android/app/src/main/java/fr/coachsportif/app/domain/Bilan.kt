package fr.coachsportif.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Bilan d'une semaine, calculé uniquement à partir de ce qui a été réellement enregistré. */
data class WeekBilan(
    val planned: Int,          // séances prévues (hors annulées)
    val done: Int,             // séances prévues terminées
    val skipped: List<String>, // titres des séances « pas faites »
    val pending: List<String>, // titres encore « à faire »
    val extra: Int,            // séances hors programme
    val runKm: Double,
    val swimM: Double,
    val walkKm: Double,
    val strengthSets: Int,
    val strengthSessions: Int,
)

fun WeekPlan.bilan(today: LocalDate = Fmt.today()): WeekBilan {
    val acts = days.flatMap { it.activities }
    val results = acts.mapNotNull { it.result } + days.flatMap { it.unplannedCompleted }
    fun dist(sport: String) = results.filter { it.sport == sport }.sumOf { it.endurance?.distanceM ?: 0.0 }
    return WeekBilan(
        planned = acts.count { it.status != "cancelled" },
        done = acts.count { it.status == "completed" },
        skipped = acts.filter { it.status == "skipped" }.map { it.title },
        pending = acts.filter { it.status in listOf("planned", "in_progress") && LocalDate.parse(it.date) <= today }.map { it.title },
        extra = days.sumOf { it.unplannedCompleted.size },
        runKm = dist("running") / 1000,
        swimM = dist("swimming"),
        walkKm = dist("walking") / 1000,
        strengthSets = results.filter { it.sport == "strength" }.sumOf { r -> r.workSetsCount ?: r.exercises.sumOf { it.workSets.size } },
        strengthSessions = results.count { it.sport == "strength" },
    )
}

/** Texte prêt à envoyer au coach (ou à afficher dans une notification). */
fun WeekPlan.bilanMessage(): String {
    val b = bilan()
    return buildString {
        appendLine("Bilan de ma semaine du ${Fmt.dayTitle(weekStart)} au ${Fmt.dayTitle(weekEnd)} :")
        appendLine("- ${b.done}/${b.planned} séances prévues faites" + (if (b.extra > 0) " + ${b.extra} hors programme" else ""))
        if (b.runKm > 0) appendLine("- Course : ${Fmt.num(b.runKm)} km")
        if (b.strengthSessions > 0) appendLine("- Muscu : ${b.strengthSessions} séance(s), ${b.strengthSets} séries")
        if (b.swimM > 0) appendLine("- Natation : ${Fmt.num(b.swimM)} m")
        if (b.walkKm > 0) appendLine("- Marche : ${Fmt.num(b.walkKm)} km")
        if (b.skipped.isNotEmpty()) appendLine("- Pas fait : ${b.skipped.joinToString(", ")}")
        if (b.pending.isNotEmpty()) appendLine("- Pas encore enregistré : ${b.pending.joinToString(", ")}")
        append("Qu'en penses-tu, et que prévois-tu pour la semaine prochaine ?")
    }
}

/** Prochaine course / événement à venir (≤ 21 jours), avec la séance « course » prévue ce jour-là si elle existe. */
data class RaceInfo(val name: String, val date: String, val daysLeft: Long, val distanceM: Int?, val activity: PlannedActivity?)

fun nextRace(weeks: List<WeekPlan>, today: LocalDate = Fmt.today()): RaceInfo? {
    val ev = weeks.flatMap { it.events }.distinctBy { it.key }
        .filter { it.eventDate != null && it.registrationStatus !in listOf("declined", "cancelled") }
        .map { it to LocalDate.parse(it.eventDate) }
        .filter { (_, d) -> !d.isBefore(today) && ChronoUnit.DAYS.between(today, d) <= 21 }
        .minByOrNull { it.second } ?: return null
    val (e, d) = ev
    val act = weeks.flatMap { w -> w.days.filter { it.date == e.eventDate }.flatMap { it.activities } }
        .firstOrNull { it.sport == e.sport || it.subtype == "race" }
    return RaceInfo(e.name, e.eventDate!!, ChronoUnit.DAYS.between(today, d), e.distanceM, act)
}
