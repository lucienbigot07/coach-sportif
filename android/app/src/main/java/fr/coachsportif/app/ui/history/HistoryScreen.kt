package fr.coachsportif.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.domain.CompletedActivity
import fr.coachsportif.app.domain.ExerciseHistory
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.VariantRow
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.today.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: Repository) : ViewModel() {
    val sessions = MutableStateFlow<List<CompletedActivity>>(emptyList())
    val variants = MutableStateFlow<List<VariantRow>>(emptyList())
    val selected = MutableStateFlow<ExerciseHistory?>(null)
    val error = MutableStateFlow<String?>(null)

    fun load() = viewModelScope.launch {
        runCatching {
            sessions.value = repo.recentSessions(50).sessions
            variants.value = repo.variants()
        }.onFailure { error.value = describe(it) }
    }

    fun open(key: String) = viewModelScope.launch {
        runCatching { selected.value = repo.exerciseHistory(key) }.onFailure { error.value = describe(it) }
    }
}

@Composable
fun HistoryScreen() {
    val vm = repoViewModel { HistoryViewModel(it) }
    val sessions by vm.sessions.collectAsState()
    val variants by vm.variants.collectAsState()
    val selected by vm.selected.collectAsState()
    val error by vm.error.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Séances") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Course") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Exercices") })
        }
        error?.let { Text("⚠️ $it", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        when (tab) {
            0 -> LazyColumn {
                items(sessions, key = { it.completedActivityId }) { s ->
                    SectionCard {
                        Text("${Fmt.sportEmoji(s.sport, s.subtype)} ${Fmt.dayTitle(s.date)}" +
                            (Fmt.splitLabel(s.subtype)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        ResultSummary(s)
                    }
                }
            }
            1 -> RunningTab(sessions.filter { it.sport == "running" && it.endurance?.paceSPerKm != null })
            else -> LazyColumn {
                selected?.let { h -> item { ExerciseDetail(h) } }
                items(variants, key = { it.key }) { v ->
                    Text("${v.name}${v.equipmentVariant?.let { " — $it" } ?: ""}",
                        Modifier.fillMaxWidth().clickable { vm.open(v.key) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun RunningTab(runs: List<CompletedActivity>) {
    val pts = runs.sortedBy { it.date }.map { it.date to (it.endurance!!.paceSPerKm!!) }
    LazyColumn {
        item {
            SectionCard {
                Text("Allure moyenne par sortie (plus bas = plus rapide)", style = MaterialTheme.typography.titleMedium)
                Text("Contexte (dénivelé, type de séance) à lire dans chaque séance.", style = MaterialTheme.typography.bodySmall)
                LineChart(pts.map { it.second }, invert = true, desc = "Courbe d'allure de ${pts.size} sorties")
                pts.lastOrNull()?.let { Text("Dernière : ${Fmt.pace(it.second)} (${it.first})") }
            }
        }
        items(runs, key = { it.completedActivityId }) { r ->
            SectionCard {
                Text("🏃 ${Fmt.dayTitle(r.date)}", style = MaterialTheme.typography.titleMedium)
                ResultSummary(r)
            }
        }
    }
}

@Composable
private fun ExerciseDetail(h: ExerciseHistory) {
    SectionCard {
        Text(h.variant.name, style = MaterialTheme.typography.titleLarge)
        h.variant.equipmentVariant?.let { Text("🏋️ $it", style = MaterialTheme.typography.bodyMedium) }
        if (h.sameNameOtherVariants.isNotEmpty())
            Text("⚠️ Autres machines du même nom, non comparables : ${h.sameNameOtherVariants.joinToString()}",
                style = MaterialTheme.typography.bodySmall)
        Fmt.historical(h.reference?.historicalReference)?.let { Text("Référence historique : $it", style = MaterialTheme.typography.bodyMedium) }
        Fmt.futureTarget(h.reference?.futureTargetNotValidated)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val loads = h.sessions.reversed().mapNotNull { s -> s.workSets.mapNotNull { it.loadKg }.maxOrNull() }
        if (loads.size >= 2) LineChart(loads, invert = false, desc = "Charge maximale par séance")
        h.sessions.forEach { s ->
            Text("${s.date} : ${Fmt.sets(s.workSets, h.variant.loadBasis)}", style = MaterialTheme.typography.bodyMedium)
        }
        if (h.sessions.isEmpty()) Text("Aucune séance saisie dans l'application pour cette variante.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LineChart(values: List<Double>, invert: Boolean, desc: String) {
    if (values.size < 2) { Text("Pas assez de données pour une courbe.", style = MaterialTheme.typography.bodySmall); return }
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 8.dp).semantics { contentDescription = desc }) {
        val min = values.min(); val max = values.max(); val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double): Float { val r = ((v - min) / span).toFloat(); return size.height * (if (invert) r else 1 - r) }
        val dx = size.width / (values.size - 1)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * dx, y(v)) } }
        drawPath(path, color, style = Stroke(width = 5f))
        values.forEachIndexed { i, v -> drawCircle(color, 7f, Offset(i * dx, y(v))) }
    }
}
