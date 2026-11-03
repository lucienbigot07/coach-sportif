package fr.coachsportif.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.domain.WeekDay
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.SportBadge
import fr.coachsportif.app.ui.components.StatusChip
import fr.coachsportif.app.ui.components.SyncLine
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.NumberLarge
import fr.coachsportif.app.ui.theme.Track
import fr.coachsportif.app.ui.theme.sportStyle
import fr.coachsportif.app.ui.today.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(monday) { vm.refresh() }
    val start = LocalDate.parse(monday)
    val days = cached?.data?.days.orEmpty()

    LazyColumn(Modifier.fillMaxSize(), state = list) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monday = start.minusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semaine précédente")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Semaine du ${start.dayOfMonth} au ${start.plusDays(6).dayOfMonth} " +
                        start.plusDays(6).month.getDisplayName(TextStyle.FULL, Locale.FRENCH),
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (monday == Fmt.mondayOf(Fmt.today()).toString())
                        Text("Cette semaine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { monday = start.plusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semaine suivante")
                }
            }
            DayStrip(start, days) { idx -> scope.launch { list.animateScrollToItem(1 + idx) } }
            SyncLine(cached?.fetchedAtMs, cached?.planVersion, error, outbox, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        items(days, key = { it.date }) { d -> DayBlock(d, onStart) }
        item {
            summary?.data?.let { s -> SummaryCard(s.planned, s.actual) }
            val evs = cached?.data?.events.orEmpty()
            if (evs.isNotEmpty()) SectionCard {
                Text("Événements", style = MaterialTheme.typography.titleMedium)
                evs.forEach { e ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(e.eventDate?.let { Fmt.dayTitle(it) } ?: "Date à fixer", style = MaterialTheme.typography.bodyMedium,
                            color = LocalExtra.current.muted, modifier = Modifier.width(140.dp))
                        Column {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(regLabel(e.registrationStatus), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Bande des 7 jours : initiale, numéro, une pastille par séance (pleine = faite). */
@Composable
private fun DayStrip(monday: LocalDate, days: List<WeekDay>, onTap: (Int) -> Unit) {
    val today = Fmt.today()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val acts = days.getOrNull(i)?.activities.orEmpty().filter { it.status !in listOf("cancelled", "skipped") }
            val isToday = d == today
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onTap(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).take(3).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.height(10.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    acts.take(4).forEach { a ->
                        val c = sportStyle(a.sport, a.subtype).color
                        Box(Modifier.size(7.dp).clip(CircleShape)
                            .background(if (a.status == "completed") c else Color.Transparent)
                            .border(1.5.dp, c, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBlock(d: WeekDay, onStart: (PlannedActivity) -> Unit) {
    val isToday = d.date == Fmt.today().toString()
    val muted = LocalExtra.current.muted
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(Fmt.dayTitle(d.date) + if (isToday) ", aujourd'hui" else "",
            style = MaterialTheme.typography.titleSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (d.activities.isEmpty() && d.unplannedCompleted.isEmpty())
            Text("Repos ou rien de publié", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        d.activities.forEach { a -> WeekRow(a, onStart) }
        d.unplannedCompleted.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                Text("Séance hors programme", style = MaterialTheme.typography.titleSmall)
                ResultSummary(r, compact = true)
            }
        }
    }
}

@Composable
private fun WeekRow(a: PlannedActivity, onStart: (PlannedActivity) -> Unit) {
    val st = sportStyle(a.sport, a.subtype)
    val muted = LocalExtra.current.muted
    val clickable = a.status in listOf("planned", "pending_confirmation", "in_progress")
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalExtra.current.border, RoundedCornerShape(16.dp))
            .then(if (clickable) Modifier.clickable { onStart(a) } else Modifier)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(st, size = 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall)
                val line = Fmt.target(a.target).take(2).joinToString(", ") { it.substringAfter(" : ") }
                if (a.result == null && line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = muted)
                if (a.lastChange != null) Text("Modifié par le coach", style = MaterialTheme.typography.labelSmall, color = Track)
            }
            StatusChip(a.status)
        }
        a.result?.let { ResultSummary(it, compact = true) }
    }
}

@Composable
private fun SummaryCard(planned: JsonObject?, actual: JsonObject?) {
    fun d(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.doubleOrNull
    SectionCard {
        Text("Bilan de la semaine", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        val kmPlan = d(planned, "running_km_firm") ?: 0.0
        val kmDone = d(actual, "running_km") ?: 0.0
        val kmPending = d(planned, "running_km_pending_confirmation") ?: 0.0
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(kmDone), style = NumberLarge)
            Text(" / ${Fmt.num(kmPlan)} km courus", style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        }
        LinearProgressIndicator(
            progress = { if (kmPlan > 0) (kmDone / kmPlan).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(CircleShape),
            color = Track, trackColor = Track.copy(alpha = 0.15f),
        )
        if (kmPending > 0) Text("+ ${Fmt.num(kmPending)} km à confirmer (non comptés dans le prévu)",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Counter("💪", d(actual, "strength_sessions"), d(planned, "strength_sessions"), "muscu", Modifier.weight(1f))
            Counter("🏊", null, d(planned, "swim_sessions"), "piscine", Modifier.weight(1f),
                doneText = d(actual, "swim_m")?.let { "${Fmt.num(it)} m" })
            Counter("⚽", d(actual, "football_sessions"), d(planned, "football_firm"), "foot", Modifier.weight(1f))
        }
        Text("Seules les séances déclarées comptent dans le réalisé.", style = MaterialTheme.typography.bodySmall,
            color = LocalExtra.current.muted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Counter(emoji: String, done: Double?, planned: Double?, label: String, modifier: Modifier, doneText: String? = null) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(emoji)
        Text(doneText ?: "${Fmt.num(done ?: 0.0)} / ${Fmt.num(planned ?: 0.0)}", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
    }
}

private fun regLabel(s: String) = when (s) {
    "confirmed" -> "Inscription confirmée"
    "envisaged_unconfirmed" -> "Envisagé, à confirmer"
    "scheduled" -> "Prévu"
    "declined", "cancelled" -> "Annulé"
    else -> s
}
