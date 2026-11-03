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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.remote.ApiException
import fr.coachsportif.app.data.remote.OfflineException
import fr.coachsportif.app.domain.ExerciseRef
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.ui.components.KeyValue
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.StatusChip
import fr.coachsportif.app.ui.components.SyncBanner
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.Ember
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
    is ApiException -> if (t.status == 401) "session expirée, reconnectez-vous" else t.code
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
    LaunchedEffect(date) { vm.refresh() } // à l'ouverture : on récupère le programme publié par le coach

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                    Text("Aujourd'hui", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(Fmt.dayTitle(date), style = MaterialTheme.typography.headlineSmall)
                }
                SyncBanner(cached?.fetchedAtMs, cached?.planVersion, error, outbox)
            }
            val plan = cached?.data
            if (plan == null) {
                item { SectionCard { Text(if (error != null) "Pas encore de données sur ce téléphone." else "Chargement…") } }
            } else if (plan.activities.isEmpty() && plan.unplannedCompleted.isEmpty()) {
                item {
                    SectionCard {
                        Text("Aucune séance publiée pour aujourd'hui", style = MaterialTheme.typography.titleMedium)
                        Text("Le programme du jour apparaît ici dès que tu le valides avec ton coach dans Claude.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(plan?.activities.orEmpty(), key = { it.sessionKey }) { a -> ActivityCard(a, onStart) }
            items(plan?.unplannedCompleted.orEmpty(), key = { it.completedActivityId }) { r ->
                SectionCard {
                    Text("${Fmt.sportEmoji(r.sport, r.subtype)} Séance hors programme", style = MaterialTheme.typography.titleMedium)
                    ResultSummary(r)
                }
            }
            item {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new")))
                }, modifier = Modifier.padding(16.dp)) {
                    Text("💬 Discuter du programme avec le coach dans Claude")
                }
                Text("Le coach relit tes séances via le connecteur Coach Sportif ; rien n'est transmis automatiquement par ce bouton.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
                Spacer(Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
fun ActivityCard(a: PlannedActivity, onStart: (PlannedActivity) -> Unit) {
    var expanded by rememberSaveable(a.sessionKey) { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Fmt.sportEmoji(a.sport, a.subtype)}  ${a.title}", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            StatusChip(a.status)
        }
        Fmt.splitLabel(a.subtype)?.takeIf { a.sport == "strength" && it != a.title }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        a.confirmedTimeLocal?.let { KeyValue("Heure", it) }
            ?: a.provisionalTimeLocal?.let { KeyValue("Heure envisagée", "$it (non confirmée)") }
        Fmt.target(a.target).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        a.notes?.let { Text("📝 $it", style = MaterialTheme.typography.bodyMedium) }
        a.lastChange?.let {
            Text("✏️ Modifié par le coach — ${it.reason ?: ""}", style = MaterialTheme.typography.bodySmall, color = Ember)
        }
        a.result?.let { ResultSummary(it) }

        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                val refs = a.exercises.ifEmpty { a.splitReferences }
                if (a.exercises.isEmpty() && refs.isNotEmpty())
                    Text("Exercices non détaillés par le coach — références de la séance ${Fmt.splitLabel(a.subtype)} :",
                        style = MaterialTheme.typography.bodySmall)
                refs.forEach { ReferenceLine(it) }
                a.lastChange?.previous?.let { Text("Avant modification : $it", style = MaterialTheme.typography.bodySmall) }
                Text("Version ${a.version}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.status in listOf("planned", "pending_confirmation", "in_progress"))
                Button(onClick = { onStart(a) }) {
                    Text(if (a.status == "pending_confirmation") "J'y suis allé" else "Commencer")
                }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Masquer" else "Voir le détail") }
        }
    }
}

@Composable
fun ReferenceLine(e: ExerciseRef) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text((if (e.isPriority) "🔴 " else "") + e.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            e.equipmentVariant?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        e.reference?.lastLogged?.let {
            Text("Dernière séance (${it.date}) : ${Fmt.sets(it.sets, e.loadBasis)}", style = MaterialTheme.typography.bodyMedium)
        } ?: Fmt.historical(e.reference?.historicalReference)?.let {
            Text("Référence : $it${Fmt.loadBasisLabel(e.loadBasis)}", style = MaterialTheme.typography.bodyMedium)
        }
        Fmt.futureTarget(e.reference?.futureTargetNotValidated)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (e.targetSets != null) Text("Prescrit : ${e.targetSets} × ${e.targetReps ?: e.targetSteps?.let { "$it pas" } ?: e.targetSeconds?.let { "$it s" } ?: "?"}" +
            (e.targetLoadKg?.let { " à ${Fmt.num(it)} kg" } ?: "") + (e.rirMin?.let { " · RIR $it–${e.rirMax ?: it}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium)
    }
}
