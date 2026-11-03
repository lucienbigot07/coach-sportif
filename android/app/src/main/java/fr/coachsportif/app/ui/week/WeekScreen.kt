package fr.coachsportif.app.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.domain.WeekDay
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.StatusChip
import fr.coachsportif.app.ui.components.SyncBanner
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.today.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

class WeekViewModel(private val repo: Repository, val monday: String) : ViewModel() {
    val week = repo.observeWeek(monday)
    val summary = repo.observeWeekSummary(monday)
    val outbox = repo.outbox
    val error = MutableStateFlow<String?>(null)
    fun refresh() = viewModelScope.launch {
        runCatching { repo.refreshWeek(monday) }.onSuccess { error.value = null }.onFailure { error.value = describe(it) }
    }
}

@Composable
fun WeekScreen(onStart: (PlannedActivity) -> Unit) {
    var monday by rememberSaveable { mutableStateOf(Fmt.mondayOf(Fmt.today()).toString()) }
    val vm = repoViewModel(key = "week-$monday") { WeekViewModel(it, monday) }
    val cached by vm.week.collectAsState(initial = null)
    val summary by vm.summary.collectAsState(initial = null)
    val outbox by vm.outbox.collectAsState(initial = emptyList())
    val error by vm.error.collectAsState()
    LaunchedEffect(monday) { vm.refresh() }
    val sunday = LocalDate.parse(monday).plusDays(6).toString()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monday = LocalDate.parse(monday).minusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semaine précédente")
                }
                Text(Fmt.weekTitle(monday, sunday), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { monday = LocalDate.parse(monday).plusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semaine suivante")
                }
            }
            SyncBanner(cached?.fetchedAtMs, cached?.planVersion, error, outbox)
            cached?.data?.rationale?.let {
                Text("🎯 $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(cached?.data?.days.orEmpty(), key = { it.date }) { d -> DayBlock(d, onStart) }
        cached?.data?.events?.takeIf { it.isNotEmpty() }?.let { evs ->
            item {
                SectionCard {
                    Text("🏁 Événements de la semaine", style = MaterialTheme.typography.titleMedium)
                    evs.forEach { e ->
                        Text("• ${e.eventDate?.let { Fmt.dayTitle(it) } ?: "date à fixer"} — ${e.name} (${regLabel(e.registrationStatus)})")
                    }
                }
            }
        }
        item {
            summary?.data?.let { s ->
                SectionCard {
                    Text("📊 Bilan de la semaine", style = MaterialTheme.typography.titleMedium)
                    Text("Prévu (ferme) : ${n(s.planned, "running_km_firm")} km de course" +
                        n(s.planned, "running_km_pending_confirmation").let { if (it != "0" && it != "?") " + $it km à confirmer" else "" } +
                        " · ${n(s.planned, "strength_sessions")} muscu · ${n(s.planned, "swim_sessions")} piscine" +
                        " · ${n(s.planned, "football_firm")} foot" +
                        n(s.planned, "football_pending_confirmation").let { if (it != "0" && it != "?") " (+$it à confirmer)" else "" },
                        style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Réalisé : ${n(s.actual, "running_km")} km de course · ${n(s.actual, "strength_sessions")} muscu " +
                        "(${n(s.actual, "strength_work_sets")} séries) · ${n(s.actual, "swim_m").let { if (it == "?") "0" else it }} m nagés" +
                        " · ${n(s.actual, "football_sessions")} foot", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Prévu ≠ réalisé : seules les séances déclarées comptent dans le réalisé.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun n(o: JsonObject?, k: String): String =
    (o?.get(k) as? JsonPrimitive)?.takeIf { it.content != "null" }?.content?.removeSuffix(".00")?.removeSuffix(".0") ?: "?"

private fun regLabel(s: String) = when (s) {
    "confirmed" -> "inscription confirmée"
    "envisaged_unconfirmed" -> "envisagé, à confirmer"
    "scheduled" -> "prévu"
    "declined", "cancelled" -> "annulé"
    else -> s
}

@Composable
private fun DayBlock(d: WeekDay, onStart: (PlannedActivity) -> Unit) {
    val isToday = d.date == Fmt.today().toString()
    SectionCard {
        Text((if (isToday) "👉 " else "") + Fmt.dayTitle(d.date), style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (d.activities.isEmpty() && d.unplannedCompleted.isEmpty())
            Text("Rien de publié", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.activities.forEach { a ->
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Fmt.sportEmoji(a.sport, a.subtype)} ${a.title}", style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusChip(a.status)
                }
                Fmt.target(a.target).take(3).forEach { Text("  · $it", style = MaterialTheme.typography.bodyMedium) }
                a.notes?.let { Text("  📝 $it", style = MaterialTheme.typography.bodySmall) }
                a.lastChange?.let { Text("  ✏️ modifié par le coach", style = MaterialTheme.typography.bodySmall) }
                a.result?.let { ResultSummary(it) }
            }
        }
        d.unplannedCompleted.forEach { ResultSummary(it) }
    }
}
