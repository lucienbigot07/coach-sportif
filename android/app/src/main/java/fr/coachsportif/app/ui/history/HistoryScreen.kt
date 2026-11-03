package fr.coachsportif.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.domain.CompletedActivity
import fr.coachsportif.app.domain.ExerciseHistory
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.VariantRow
import fr.coachsportif.app.ui.components.Banner
import fr.coachsportif.app.ui.components.InfoBox
import fr.coachsportif.app.ui.components.LaneCard
import fr.coachsportif.app.ui.components.ResultSummary
import fr.coachsportif.app.ui.components.ScreenHeader
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.SportBadge
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.Ember
import fr.coachsportif.app.ui.theme.Iron
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.NumberLarge
import fr.coachsportif.app.ui.theme.Track
import fr.coachsportif.app.ui.theme.sportStyle
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

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(kicker = null, title = "Progrès")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Séances", "Course", "Exercices").forEachIndexed { i, l ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(l) })
                }
            }
            error?.let { Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { Banner(Ember, "Impossible de charger : $it") } }
        }
        when (tab) {
            0 -> items(sessions, key = { it.completedActivityId }) { s ->
                val st = sportStyle(s.sport, s.subtype)
                LaneCard(st.color) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportBadge(st, size = 36); Spacer(Modifier.width(10.dp))
                        Column {
                            Text(Fmt.dayTitle(s.date), style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.splitLabel(s.subtype)?.takeIf { s.sport == "strength" } ?: st.label,
                                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                    }
                    ResultSummary(s, compact = s.sport == "strength" && s.exercises.size > 4)
                }
            }
            1 -> {
                val runs = sessions.filter { it.sport == "running" && it.endurance?.paceSPerKm != null }
                item { PaceCard(runs) }
                items(runs, key = { "r" + it.completedActivityId }) { r ->
                    LaneCard(sportStyle("running").color) {
                        Text(Fmt.dayTitle(r.date), style = MaterialTheme.typography.titleSmall)
                        ResultSummary(r, compact = true)
                    }
                }
            }
            else -> {
                selected?.let { h -> item { ExerciseDetail(h) } }
                val groups = variants.groupBy { it.split }
                groups.forEach { (split, list) ->
                    item(key = "h-$split") {
                        Text(Fmt.splitLabel(split) ?: "Autres", style = MaterialTheme.typography.titleSmall,
                            color = LocalExtra.current.muted, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.key }) { v ->
                        val sel = selected?.variant?.variantKey == v.key
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.open(v.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            v.equipmentVariant?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PaceCard(runs: List<CompletedActivity>) {
    val pts = runs.sortedBy { it.date }.map { it.endurance!!.paceSPerKm!! }
    SectionCard {
        Text("Allure moyenne par sortie", style = MaterialTheme.typography.titleMedium)
        runs.firstOrNull()?.let { last ->
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text(Fmt.pace(last.endurance?.paceSPerKm)?.removeSuffix("/km") ?: "?", style = NumberLarge)
                Text("  min/km, le ${last.date}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        }
        LineChart(pts, invert = true, color = Track, desc = "Allure de ${pts.size} sorties", fmt = { Fmt.pace(it)?.removeSuffix("/km") ?: "" })
        Text("Plus haut = plus rapide. Le dénivelé et le type de séance comptent : lis chaque sortie.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
    }
}

@Composable
private fun ExerciseDetail(h: ExerciseHistory) {
    val muted = LocalExtra.current.muted
    SectionCard {
        Text(h.variant.name, style = MaterialTheme.typography.titleLarge)
        h.variant.equipmentVariant?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        if (h.sameNameOtherVariants.isNotEmpty())
            Banner(MaterialTheme.colorScheme.tertiary, "Autre(s) machine(s) du même nom, à ne pas comparer : ${h.sameNameOtherVariants.joinToString()}")
        Spacer(Modifier.height(8.dp))
        InfoBox {
            Fmt.historical(h.reference?.historicalReference)?.let { Text("Référence : $it", style = MaterialTheme.typography.labelMedium) }
            Fmt.futureTarget(h.reference?.futureTargetNotValidated)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        val loads = h.sessions.reversed().mapNotNull { s -> s.workSets.mapNotNull { it.loadKg }.maxOrNull() }
        if (loads.size >= 2) LineChart(loads, invert = false, color = Iron, desc = "Charge maximale par séance", fmt = { "${Fmt.num(it)} kg" })
        h.sessions.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(s.date, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(96.dp))
                Text(Fmt.sets(s.workSets, h.variant.loadBasis), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (h.sessions.isEmpty()) Text("Pas encore de séance saisie dans l'appli pour cette machine.",
            style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LineChart(values: List<Double>, invert: Boolean, color: Color, desc: String, fmt: (Double) -> String) {
    if (values.size < 2) {
        Text("Il faut au moins deux séances pour tracer une courbe.", style = MaterialTheme.typography.bodySmall,
            color = LocalExtra.current.muted, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val grid = LocalExtra.current.border
    val min = values.min(); val max = values.max()
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(fmt(if (invert) min else max), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 6.dp).semantics { contentDescription = desc }) {
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            fun y(v: Double): Float { val r = ((v - min) / span).toFloat(); return size.height * (if (invert) r else 1 - r) }
            val dx = size.width / (values.size - 1)
            listOf(0f, size.height / 2, size.height).forEach { gy ->
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * dx, y(v)) } }
            drawPath(path, color, style = Stroke(width = 6f))
            values.forEachIndexed { i, v ->
                drawCircle(Color.White, 8f, Offset(i * dx, y(v)))
                drawCircle(color, 8f, Offset(i * dx, y(v)), style = Stroke(width = 4f))
            }
        }
        Text(fmt(if (invert) max else min), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
    }
}
