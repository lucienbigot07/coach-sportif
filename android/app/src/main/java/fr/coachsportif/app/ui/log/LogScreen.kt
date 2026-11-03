package fr.coachsportif.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.data.SaveOutcome
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.repoViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(date: String, sessionKey: String, onDone: () -> Unit) {
    val vm = repoViewModel(key = "log-$sessionKey") { LogViewModel(it, date, sessionKey) }
    val ui by vm.ui.collectAsState()
    val saving by vm.saving.collectAsState()
    val outcome by vm.outcome.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text((ui as? LogUi.Ready)?.activity?.let { "${Fmt.sportEmoji(it.sport, it.subtype)} ${it.title}" } ?: "Séance") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        when (val s = ui) {
            LogUi.Loading -> Text("Chargement…", Modifier.padding(pad).padding(16.dp))
            is LogUi.Missing -> Text(s.reason, Modifier.padding(pad).padding(16.dp))
            is LogUi.Ready -> LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding()) {
                item {
                    Text("Brouillon sauvegardé automatiquement sur le téléphone.", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                if (s.activity.sport == "strength") {
                    itemsIndexed(s.draft.exercises, key = { _, e -> e.variantKey }) { i, e -> ExerciseBlock(vm, i, e) }
                } else {
                    item { EnduranceBlock(vm, s.activity, s.draft.endurance) }
                }
                item { FinishBlock(vm, s.draft, saving) { confirm = true } }
            }
        }
    }

    val ready = ui as? LogUi.Ready
    if (confirm && ready != null) {
        val n = vm.workSetsReady(ready.draft)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Terminer la séance ?") },
            text = {
                Text(if (ready.activity.sport == "strength") "$n série(s) de travail validée(s) seront enregistrées. " +
                    "Les séries non cochées ou sans répétitions sont ignorées." else "Le résultat sera enregistré comme réalisé.")
            },
            confirmButton = { Button(onClick = { confirm = false; vm.finish() }) { Text("Terminer") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Continuer") } },
        )
    }
    outcome?.let { o ->
        AlertDialog(
            onDismissRequest = { vm.clearOutcome() },
            title = {
                Text(when (o) {
                    is SaveOutcome.Synced -> "✅ Enregistré et synchronisé"
                    SaveOutcome.Queued -> "📤 Enregistré sur le téléphone"
                    is SaveOutcome.Rejected -> "❌ Refusé par le serveur"
                })
            },
            text = {
                Text(when (o) {
                    is SaveOutcome.Synced -> "Ton coach pourra relire cette séance dans Claude."
                    SaveOutcome.Queued -> "Pas de réseau : l'envoi se fera automatiquement, sans doublon, dès le retour de la connexion."
                    is SaveOutcome.Rejected -> "Rien n'a été écrit côté serveur (${o.code}). ${o.detail ?: ""}"
                })
            },
            confirmButton = {
                Button(onClick = { vm.clearOutcome(); if (o !is SaveOutcome.Rejected) onDone() }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ExerciseBlock(vm: LogViewModel, i: Int, e: ExDraft) {
    SectionCard {
        Text((if (e.priority) "🔴 " else "") + e.name, style = MaterialTheme.typography.titleMedium)
        e.equipment?.let { Text("🏋️ $it", style = MaterialTheme.typography.bodySmall) }
        e.refLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        e.targetLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val perfLabel = when (e.unit) { "steps" -> "Pas"; "seconds" -> "Sec."; "round_trips" -> "A/R"; else -> "Rép." }
        var work = 0; var warm = 0
        e.sets.forEachIndexed { j, s ->
            val label = if (s.isWarmup) "É${++warm}" else "S${++work}"
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge)
                if (e.loadBasis != "bodyweight")
                    NumField(s.load, "kg" + if (e.loadBasis == "per_hand") "/main" else "", Modifier.weight(1f), decimal = true) {
                        v -> vm.editSet(i, j) { it.copy(load = v) }
                    }
                if (j > 0 && e.loadBasis != "bodyweight")
                    IconButton(onClick = { vm.copyPreviousLoad(i, j) }) { Icon(Icons.Filled.ContentCopy, "Reprendre la charge précédente") }
                Spacer(Modifier.width(6.dp))
                NumField(s.perf, perfLabel, Modifier.weight(1f)) { v -> vm.editSet(i, j) { it.copy(perf = v) } }
                Checkbox(checked = s.done, onCheckedChange = { c -> vm.editSet(i, j) { it.copy(done = c) } })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.addSet(i, warmup = false) }) { Text("+ série") }
            TextButton(onClick = { vm.addSet(i, warmup = true) }) { Text("+ échauffement") }
        }
    }
}

@Composable
private fun EnduranceBlock(vm: LogViewModel, a: PlannedActivity, d: EnduranceDraft) {
    fun set(f: (EnduranceDraft) -> EnduranceDraft) = vm.edit { it.copy(endurance = f(it.endurance)) }
    SectionCard {
        val swim = a.sport == "swimming"
        val foot = a.sport == "football"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(d.distance, if (swim) "Distance (m)" else "Distance (km)", Modifier.weight(1f), decimal = true) { v -> set { it.copy(distance = v) } }
            OutlinedTextField(d.duration, { v -> set { it.copy(duration = v) } }, Modifier.weight(1f),
                label = { Text(if (foot) "Temps de jeu (mm:ss)" else "Durée (mm:ss)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        val pace = run {
            val km = Fmt.parseDecimal(d.distance)?.let { if (swim) it / 1000 else it }
            val s = Fmt.parseDuration(d.duration)
            if (km != null && km > 0 && s != null && !swim) Fmt.pace(s / km) else null
        }
        pace?.let { Text("Allure calculée : $it", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            NumField(d.avgHr, "FC moy.", Modifier.weight(1f)) { v -> set { it.copy(avgHr = v) } }
            NumField(d.maxHr, "FC max", Modifier.weight(1f)) { v -> set { it.copy(maxHr = v) } }
            if (!swim) NumField(d.ascent, "D+ (m)", Modifier.weight(1f)) { v -> set { it.copy(ascent = v) } }
            else NumField(d.poolLength, "Bassin (m)", Modifier.weight(1f)) { v -> set { it.copy(poolLength = v) } }
        }
        Text("Type d'effort (ne pas déduire « facile » d'un ressenti bas)", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp))
        val types = when {
            foot -> listOf("entraînement", "match")
            swim -> listOf("facile", "technique", "fractionné")
            else -> listOf("facile", "tempo", "fractionné", "course", "trail")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { t -> FilterChip(selected = d.effortType == t, onClick = { set { it.copy(effortType = if (it.effortType == t) null else t) } },
                label = { Text(t) }) }
        }
    }
}

@Composable
private fun FinishBlock(vm: LogViewModel, d: LogDraft, saving: Boolean, onFinish: () -> Unit) {
    SectionCard {
        Text("Ressenti global : ${d.rpe?.let { "$it/10" } ?: "non renseigné"}", style = MaterialTheme.typography.titleMedium)
        Slider(value = (d.rpe ?: 5).toFloat(), onValueChange = { v -> vm.edit { it.copy(rpe = v.roundToInt()) } },
            valueRange = 1f..10f, steps = 8)
        OutlinedTextField(d.notes, { v -> vm.edit { it.copy(notes = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Notes, douleurs, ressenti") }, minLines = 2)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onFinish, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Enregistrement…" else "Terminer la séance")
        }
    }
}

@Composable
private fun NumField(value: String, label: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onChange(v) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}
