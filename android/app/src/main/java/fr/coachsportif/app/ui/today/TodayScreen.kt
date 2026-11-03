package fr.coachsportif.app.ui.today

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.remote.ApiException
import fr.coachsportif.app.data.remote.OfflineException
import fr.coachsportif.app.domain.ExerciseRef
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.ui.components.InfoBox
import fr.coachsportif.app.ui.components.LaneCard
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.ScreenHeader
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.SegmentBar
import fr.coachsportif.app.ui.components.SportBadge
import fr.coachsportif.app.ui.components.StatusChip
import fr.coachsportif.app.ui.components.SyncLine
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.Amber
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.sportStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DayViewModel(private val repo: Repository, val date: String) : ViewModel() {
    val day = repo.observeDay(date)
    val outbox = repo.outbox
    val error = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        runCatching { repo.flush(); repo.refreshDay(date) }
            .onSuccess { error.value = null }
            .onFailure { error.value = describe(it) }
        refreshing.value = false
    }
}

fun describe(t: Throwable): String = when (t) {
    is OfflineException -> "pas de réseau"
    is ApiException -> if (t.status == 401) "session expirée, reconnecte-toi" else t.code
    else -> t.message ?: "erreur inconnue"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(date: String = Fmt.today().toString(), onStart: (PlannedActivity) -> Unit) {
    val vm = repoViewModel(key = "day-$date") { DayViewModel(it, date) }
    val cached by vm.day.collectAsState(initial = null)
    val outbox by vm.outbox.collectAsState(initial = emptyList())
    val error by vm.error.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(date) { vm.refresh() }

    val plan = cached?.data
    val acts = plan?.activities.orEmpty().filter { it.status !in listOf("cancelled", "skipped") }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ScreenHeader(kicker = "Aujourd'hui", title = Fmt.dayTitle(date))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (acts.isNotEmpty()) {
                        val done = acts.count { it.status == "completed" }
                        Text(
                            when (done) {
                                0 -> "${acts.size} séance(s) au programme"
                                acts.size -> "Tout est fait. Bonne récupération."
                                else -> "$done sur ${acts.size} faite(s)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        SegmentBar(acts.map { sportStyle(it.sport, it.subtype).color to (it.status == "completed") })
                        Spacer(Modifier.height(12.dp))
                    }
                    SyncLine(cached?.fetchedAtMs, cached?.planVersion, error, outbox)
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (plan == null) {
                item { SectionCard { Text(if (error != null) "Aucune donnée sur ce téléphone pour l'instant. Glisse vers le bas quand tu as du réseau." else "Chargement…") } }
            } else if (plan.activities.isEmpty() && plan.unplannedCompleted.isEmpty()) {
                item {
                    SectionCard {
                        Text("Aucune séance publiée pour aujourd'hui", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Valide ton programme avec ton coach dans Claude : il apparaîtra ici.",
                            style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                    }
                }
            }
            items(plan?.activities.orEmpty(), key = { it.sessionKey }) { a -> ActivityCard(a, onStart) }
            items(plan?.unplannedCompleted.orEmpty(), key = { it.completedActivityId }) { r ->
                val st = sportStyle(r.sport, r.subtype)
                LaneCard(st.color) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportBadge(st); Spacer(Modifier.width(12.dp))
                        Text("Séance hors programme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip("completed")
                    }
                    ResultSummary(r)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new"))) }) {
                        Text("Parler du programme avec le coach")
                    }
                    Text("Ouvre Claude. Le coach relit tes séances avec le connecteur Coach Sportif.",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ActivityCard(a: PlannedActivity, onStart: (PlannedActivity) -> Unit) {
    var expanded by rememberSaveable(a.sessionKey) { mutableStateOf(false) }
    val st = sportStyle(a.sport, a.subtype)
    val muted = LocalExtra.current.muted
    LaneCard(st.color, dimmed = a.status in listOf("cancelled", "skipped")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(st)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleLarge)
                val sub = listOfNotNull(
                    Fmt.splitLabel(a.subtype)?.takeIf { a.sport == "strength" && it != a.title },
                    a.confirmedTimeLocal?.let { "à $it" } ?: a.provisionalTimeLocal?.let { "vers $it, non confirmé" },
                )
                if (sub.isNotEmpty()) Text(sub.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(a.status)
        }

        val consignes = Fmt.target(a.target)
        if (a.result == null && consignes.isNotEmpty()) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                consignes.forEach { c ->
                    val (k, v) = c.split(" : ", limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to it[0] }
                    Row {
                        if (k.isNotEmpty()) Text(k, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.width(110.dp))
                        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        a.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp)) }
        a.lastChange?.let {
            Text("Modifié par le coach" + (it.reason?.let { r -> " : $r" } ?: ""), style = MaterialTheme.typography.bodySmall,
                color = Amber, modifier = Modifier.padding(top = 8.dp))
        }
        a.result?.let { ResultSummary(it) }

        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val refs = a.exercises.ifEmpty { a.splitReferences }
                if (a.exercises.isEmpty() && refs.isNotEmpty())
                    Text("Le coach n'a pas détaillé les exercices. Voici tes références ${Fmt.splitLabel(a.subtype)} :",
                        style = MaterialTheme.typography.bodySmall, color = muted)
                refs.forEach { ReferenceLine(it) }
                Text("Version ${a.version} du coach", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (a.status in listOf("planned", "pending_confirmation", "in_progress"))
                Button(onClick = { onStart(a) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(if (a.status == "pending_confirmation") "J'y suis allé" else "Commencer")
                }
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text(if (expanded) "Masquer" else "Détails")
            }
        }
    }
}

@Composable
fun ReferenceLine(e: ExerciseRef) {
    val muted = LocalExtra.current.muted
    InfoBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (e.isPriority) "🔴 " else "") + e.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        e.equipmentVariant?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        e.reference?.lastLogged?.let {
            Text("Dernière fois (${it.date}) : ${Fmt.sets(it.sets, e.loadBasis)}", style = MaterialTheme.typography.labelMedium)
        } ?: Fmt.historical(e.reference?.historicalReference)?.let {
            Text("Référence : $it${Fmt.loadBasisLabel(e.loadBasis)}", style = MaterialTheme.typography.labelMedium)
        }
        if (e.targetSets != null) Text(
            "Prescrit : ${e.targetSets} × ${e.targetReps ?: e.targetSteps?.let { "$it pas" } ?: e.targetSeconds?.let { "$it s" } ?: "?"}" +
                (e.targetLoadKg?.let { " à ${Fmt.num(it)} kg" } ?: "") + (e.rirMin?.let { ", RIR $it–${e.rirMax ?: it}" } ?: ""),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Fmt.futureTarget(e.reference?.futureTargetNotValidated)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    }
}
