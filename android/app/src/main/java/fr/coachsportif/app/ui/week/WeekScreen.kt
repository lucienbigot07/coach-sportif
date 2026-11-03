package fr.coachsportif.app.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.domain.CompletedActivity
import fr.coachsportif.app.domain.EventOut
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.domain.WeekDay
import fr.coachsportif.app.ui.components.StatusChip
import fr.coachsportif.app.ui.components.SportBadge
import fr.coachsportif.app.ui.components.SyncLine
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.NumberLarge
import fr.coachsportif.app.ui.theme.Pitch
import fr.coachsportif.app.ui.theme.Pool
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

private val HIDDEN = listOf("cancelled", "skipped")

@Composable
fun WeekScreen(onStart: (PlannedActivity) -> Unit) {
    val thisMonday = Fmt.mondayOf(Fmt.today())
    var monday by rememberSaveable { mutableStateOf(thisMonday.toString()) }
    val start = LocalDate.parse(monday)
    var selected by rememberSaveable(monday) {
        mutableStateOf(if (start == thisMonday) (Fmt.today().dayOfWeek.value - 1) else 0)
    }
    val vm = repoViewModel(key = "week-$monday") { WeekViewModel(it, monday) }
    val cached by vm.week.collectAsState(initial = null)
    val summary by vm.summary.collectAsState(initial = null)
    val outbox by vm.outbox.collectAsState(initial = emptyList())
    val error by vm.error.collectAsState()
    LaunchedEffect(monday) { vm.refresh() }
    val days = cached?.data?.days.orEmpty()
    val events = cached?.data?.events.orEmpty()

    LazyColumn(Modifier.fillMaxSize()) {
        // --- en-tête : titre + navigation entre semaines
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (start == thisMonday) "Cette semaine" else "Semaine", style = MaterialTheme.typography.headlineSmall)
                    Text("${start.dayOfMonth} – ${start.plusDays(6).dayOfMonth} " +
                        start.plusDays(6).month.getDisplayName(TextStyle.FULL, Locale.FRENCH),
                        style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                }
                IconButton(onClick = { monday = start.minusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semaine précédente")
                }
                IconButton(onClick = { monday = start.plusWeeks(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semaine suivante")
                }
            }
        }
        // --- résumé de la semaine
        item { SummaryCard(summary?.data?.planned, summary?.data?.actual) }
        // --- sélecteur de jour
        item { DayPicker(start, days, selected) { selected = it } }
        item {
            SyncLine(cached?.fetchedAtMs, cached?.planVersion, error, outbox, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        // --- détail du jour choisi
        item {
            AnimatedContent(targetState = selected, label = "jour") { idx ->
                val date = start.plusDays(idx.toLong())
                DayDetail(date, days.getOrNull(idx), events.filter { it.eventDate == date.toString() }, onStart)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------------------------ résumé */

@Composable
private fun SummaryCard(planned: JsonObject?, actual: JsonObject?) {
    fun d(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.doubleOrNull
    val kmPlan = d(planned, "running_km_firm") ?: 0.0
    val kmDone = d(actual, "running_km") ?: 0.0
    val kmPending = d(planned, "running_km_pending_confirmation") ?: 0.0
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtra.current.border),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Ring(if (kmPlan > 0) (kmDone / kmPlan).toFloat() else 0f, Track, Modifier.size(104.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.num(kmDone), style = NumberLarge)
                    Text("sur ${Fmt.num(kmPlan)} km", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatLine(sportStyle("strength").color, "Muscu", d(actual, "strength_sessions"), d(planned, "strength_sessions"))
                StatLine(Pool, "Piscine", null, d(planned, "swim_sessions"),
                    override = d(actual, "swim_m")?.let { "${Fmt.num(it)} m" })
                StatLine(Pitch, "Foot", d(actual, "football_sessions"), d(planned, "football_firm"))
                if (kmPending > 0) Text("+ ${Fmt.num(kmPending)} km à confirmer", style = MaterialTheme.typography.labelSmall,
                    color = LocalExtra.current.muted)
            }
        }
    }
}

@Composable
private fun Ring(progress: Float, color: Color, modifier: Modifier) {
    val track = color.copy(alpha = 0.15f)
    Canvas(modifier.semantics { contentDescription = "Course : ${(progress * 100).toInt()} % du prévu" }) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), sz,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun StatLine(color: Color, label: String, done: Double?, planned: Double?, override: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(override ?: "${Fmt.num(done ?: 0.0)}/${Fmt.num(planned ?: 0.0)}", style = MaterialTheme.typography.labelLarge)
    }
}

/* ------------------------------------------------------------------ sélecteur de jours */

@Composable
private fun DayPicker(monday: LocalDate, days: List<WeekDay>, selected: Int, onSelect: (Int) -> Unit) {
    val today = Fmt.today()
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val acts = days.getOrNull(i)?.activities.orEmpty().filter { it.status !in HIDDEN }
            val isSel = i == selected
            val isToday = d == today
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (isSel) cs.primary else cs.surface)
                    .border(1.dp, if (isToday && !isSel) cs.primary else LocalExtra.current.border, RoundedCornerShape(18.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp)
                    .semantics { contentDescription = "${Fmt.dayTitle(d.toString())}, ${acts.size} séance(s)" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).take(3).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall, color = if (isSel) cs.onPrimary else LocalExtra.current.muted)
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (isSel) cs.onPrimary else cs.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    acts.take(3).forEach { a ->
                        val c = if (isSel) cs.onPrimary else sportStyle(a.sport, a.subtype).color
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (a.status == "completed") c else c.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ détail du jour */

@Composable
private fun DayDetail(date: LocalDate, day: WeekDay?, events: List<EventOut>, onStart: (PlannedActivity) -> Unit) {
    val acts = day?.activities.orEmpty().filter { it.status !in HIDDEN }
    val unplanned = day?.unplannedCompleted.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(Fmt.dayTitle(date.toString()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp))
        events.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                    Text(regLabel(e.registrationStatus), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
            }
        }
        if (acts.isEmpty() && unplanned.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.SelfImprovement, null, tint = LocalExtra.current.muted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Rien de prévu ce jour-là", style = MaterialTheme.typography.titleSmall)
                Text("Repos, ou séance pas encore publiée par le coach.", style = MaterialTheme.typography.bodySmall,
                    color = LocalExtra.current.muted)
            }
        }
        acts.forEach { a -> SessionTile(a, onStart) }
        unplanned.forEach { r -> CompletedTile(r) }
    }
}

@Composable
private fun SessionTile(a: PlannedActivity, onStart: (PlannedActivity) -> Unit) {
    val st = sportStyle(a.sport, a.subtype)
    val muted = LocalExtra.current.muted
    val actionable = a.status in listOf("planned", "pending_confirmation", "in_progress")
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp))
            .then(if (actionable) Modifier.clickable { onStart(a) } else Modifier),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtra.current.border),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportBadge(st, size = 46, filled = a.status == "completed")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        a.confirmedTimeLocal ?: a.provisionalTimeLocal?.let { "vers $it" },
                        Fmt.target(a.target).firstOrNull()?.substringAfter(" : "),
                    ).joinToString("  ·  ")
                    if (a.result == null && sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(a.status)
            }
            a.result?.endurance?.let { e ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOfNotNull(
                        Fmt.km(e.distanceM)?.let { it to "distance" },
                        Fmt.duration(e.durationS)?.let { it to "durée" },
                        Fmt.pace(e.paceSPerKm)?.removeSuffix("/km")?.let { it to "min/km" },
                        e.avgHr?.let { "$it" to "bpm" },
                    ).forEach { (v, l) ->
                        Column(Modifier.weight(1f)) {
                            Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(l, style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
            }
            val res = a.result
            if (res?.workSetsCount != null) {
                Text("${res.workSetsCount} séries · ${res.exercises.size} exercices" + (res.rpe10?.let { r -> " · ressenti ${Fmt.num(r)}/10" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (a.lastChange != null && a.result == null)
                Text("Modifié par le coach", style = MaterialTheme.typography.labelSmall, color = Track, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CompletedTile(r: CompletedActivity) {
    val st = sportStyle(r.sport, r.subtype)
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtra.current.border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SportBadge(st, size = 46, filled = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${st.label} hors programme", style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(Fmt.km(r.endurance?.distanceM), Fmt.duration(r.endurance?.durationS),
                    r.workSetsCount?.let { "$it séries" }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            StatusChip("completed")
        }
    }
}

private fun regLabel(s: String) = when (s) {
    "confirmed" -> "Inscription confirmée"
    "envisaged_unconfirmed" -> "Envisagé, à confirmer"
    "scheduled" -> "Prévu"
    "declined", "cancelled" -> "Annulé"
    else -> s
}

