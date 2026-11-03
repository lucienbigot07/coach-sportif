package fr.coachsportif.app.ui.log

import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.ui.platform.LocalContext
import java.net.URLEncoder
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.data.SaveOutcome
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.ui.components.InfoBox
import fr.coachsportif.app.ui.components.LaneCard
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.components.SportBadge
import fr.coachsportif.app.ui.components.repoViewModel
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.NumberLarge
import fr.coachsportif.app.ui.theme.sportStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(date: String, sessionKey: String, onDone: () -> Unit) {
    val vm = repoViewModel(key = "log-$sessionKey") { LogViewModel(it, date, sessionKey) }
    val ui by vm.ui.collectAsState()
    val saving by vm.saving.collectAsState()
    val outcome by vm.outcome.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val ready = ui as? LogUi.Ready
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ready?.activity?.let { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SportBadge(sportStyle(a.sport, a.subtype), size = 34)
                            Spacer(Modifier.width(10.dp))
                            Text(a.title, style = MaterialTheme.typography.titleLarge)
                        }
                    } ?: Text("Séance")
                },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    if (ready != null) TextButton(onClick = { askCoach(ctx, vm.coachMessage()) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Coach")
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null) Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (ready.activity.sport == "strength") {
                            Text("${vm.workSetsReady(ready.draft)}", style = NumberLarge)
                            Text("séries validées", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
                        } else Text("Brouillon enregistré", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                    }
                    Button(onClick = { confirm = true }, enabled = !saving, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)) {
                        Text(if (saving) "Enregistrement…" else "Terminer la séance")
                    }
                }
            }
        },
    ) { pad ->
        when (val s = ui) {
            LogUi.Loading -> Text("Chargement…", Modifier.padding(pad).padding(20.dp))
            is LogUi.Missing -> Text(s.reason, Modifier.padding(pad).padding(20.dp))
            is LogUi.Ready -> LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding()) {
                item {
                    Text("Tout est sauvegardé sur le téléphone à chaque saisie. Coche une série quand elle est faite.",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                if (s.activity.sport == "strength") {
                    itemsIndexed(s.draft.exercises, key = { i, e -> "$i-${e.variantKey}" }) { i, e -> ExerciseBlock(vm, i, e) }
                    item {
                        OutlinedButton(
                            onClick = { vm.loadCatalog(); adding = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter un exercice")
                        }
                        Text("Hors programme : le coach le verra comme un écart à la séance prévue.",
                            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                } else {
                    item { EnduranceBlock(vm, s.activity, s.draft.endurance) }
                }
                item { FeelingBlock(vm, s.draft) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (adding && ready != null) AddExerciseDialog(vm, ready.activity.subtype) { adding = false }

    if (confirm && ready != null) {
        val n = vm.workSetsReady(ready.draft)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Terminer la séance ?") },
            text = {
                Text(if (ready.activity.sport == "strength") "$n série(s) de travail seront enregistrées. Les séries non cochées ou sans répétitions sont ignorées."
                     else "Le résultat sera enregistré comme réalisé.")
            },
            confirmButton = { Button(onClick = { confirm = false; vm.finish() }) { Text("Terminer") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Continuer la séance") } },
        )
    }
    outcome?.let { o ->
        AlertDialog(
            onDismissRequest = { vm.clearOutcome() },
            title = {
                Text(when (o) {
                    is SaveOutcome.Synced -> "Séance enregistrée"
                    SaveOutcome.Queued -> "Enregistrée sur le téléphone"
                    is SaveOutcome.Rejected -> "Séance refusée par le serveur"
                })
            },
            text = {
                Text(when (o) {
                    is SaveOutcome.Synced -> "Ton coach pourra la relire dans Claude."
                    SaveOutcome.Queued -> "Pas de réseau. L'envoi se fera tout seul, sans doublon, dès que la connexion revient."
                    is SaveOutcome.Rejected -> "Rien n'a été écrit (${o.code}). ${o.detail ?: ""}"
                })
            },
            confirmButton = { Button(onClick = { vm.clearOutcome(); if (o !is SaveOutcome.Rejected) onDone() }) { Text("OK") } },
        )
    }
}

@Composable
private fun ExerciseBlock(vm: LogViewModel, i: Int, e: ExDraft) {
    val muted = LocalExtra.current.muted
    val doneCount = e.sets.count { it.done && !it.isWarmup }
    LaneCard(when { e.priority -> MaterialTheme.colorScheme.secondary; e.added -> MaterialTheme.colorScheme.tertiary
                    else -> sportStyle("strength").color }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (e.priority) "🔴 " else "") + e.name, style = MaterialTheme.typography.titleLarge)
                e.equipment?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            Text("$doneCount/${e.sets.count { !it.isWarmup }}", style = MaterialTheme.typography.labelLarge,
                color = if (doneCount > 0) MaterialTheme.colorScheme.primary else muted)
            if (e.added) IconButton(onClick = { vm.removeAdded(i) }) {
                Icon(Icons.Filled.Close, "Retirer cet exercice ajouté", tint = muted)
            }
        }
        if (e.added) Text("Ajouté hors programme", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp))
        if (e.refLine != null || e.targetLine != null) {
            Spacer(Modifier.height(10.dp))
            InfoBox {
                e.refLine?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                e.targetLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
        }
        val perfLabel = when (e.unit) { "steps" -> "Pas"; "seconds" -> "Secondes"; "round_trips" -> "A/R"; else -> "Rép." }
        val hasLoad = e.loadBasis != "bodyweight"
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Série", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = muted)
            if (hasLoad) Text("Charge (kg${if (e.loadBasis == "per_hand") "/main" else ""})", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = muted)
            Text(perfLabel, Modifier.weight(1f).padding(start = if (hasLoad) 8.dp else 0.dp), style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.width(48.dp))
        }
        var work = 0; var warm = 0
        e.sets.forEachIndexed { j, s ->
            val label = if (s.isWarmup) "É${++warm}" else "S${++work}"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge,
                    color = if (s.isWarmup) muted else MaterialTheme.colorScheme.onSurface)
                if (hasLoad) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    NumField(s.load, Modifier.weight(1f), decimal = true) { v -> vm.editSet(i, j) { it.copy(load = v) } }
                    if (j > 0) IconButton(onClick = { vm.copyPreviousLoad(i, j) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, "Reprendre la charge précédente", Modifier.size(18.dp), tint = muted)
                    }
                }
                Spacer(Modifier.width(8.dp))
                NumField(s.perf, Modifier.weight(1f)) { v -> vm.editSet(i, j) { it.copy(perf = v) } }
                Spacer(Modifier.width(8.dp))
                DoneToggle(s.done) { c -> vm.editSet(i, j) { it.copy(done = c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.addSet(i, warmup = false) }) { Text("Ajouter une série") }
            TextButton(onClick = { vm.addSet(i, warmup = true) }) { Text("Échauffement") }
        }
        OutlinedTextField(
            value = e.comment,
            onValueChange = { vm.editComment(i, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Commentaire : sensations, réglage du siège, prise…") },
            leadingIcon = { Icon(Icons.Filled.ChatBubbleOutline, null, tint = muted) },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(12.dp),
            minLines = 1, maxLines = 4,
        )
    }
}

/** Gros bouton rond : facile à toucher d'une main entre deux séries. */
@Composable
private fun DoneToggle(done: Boolean, onChange: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (done) primary else Color.Transparent)
            .border(2.dp, if (done) primary else LocalExtra.current.border, CircleShape)
            .clickable { onChange(!done) }
            .semantics { contentDescription = if (done) "Série validée" else "Valider la série" },
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun EnduranceBlock(vm: LogViewModel, a: PlannedActivity, d: EnduranceDraft) {
    fun set(f: (EnduranceDraft) -> EnduranceDraft) = vm.edit { it.copy(endurance = f(it.endurance)) }
    val st = sportStyle(a.sport, a.subtype)
    val swim = a.sport == "swimming"
    val foot = a.sport == "football"
    LaneCard(st.color) {
        Text("Résultat", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(if (swim) "Distance (m)" else "Distance (km)", d.distance, Modifier.weight(1f), decimal = true) { v -> set { it.copy(distance = v) } }
            LabeledField(if (foot) "Temps de jeu" else "Durée", d.duration, Modifier.weight(1f), placeholder = "mm:ss", time = true) { v -> set { it.copy(duration = v) } }
        }
        val pace = run {
            val km = Fmt.parseDecimal(d.distance)?.let { if (swim) it / 1000 else it }
            val sec = Fmt.parseDuration(d.duration)
            if (km != null && km > 0 && sec != null && !swim) Fmt.pace(sec / km) else null
        }
        if (pace != null) InfoBox(Modifier.padding(top = 10.dp)) {
            Text(pace.removeSuffix("/km"), style = NumberLarge)
            Text("min/km, allure calculée", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField("FC moy.", d.avgHr, Modifier.weight(1f)) { v -> set { it.copy(avgHr = v) } }
            LabeledField("FC max", d.maxHr, Modifier.weight(1f)) { v -> set { it.copy(maxHr = v) } }
            if (!swim) LabeledField("D+ (m)", d.ascent, Modifier.weight(1f)) { v -> set { it.copy(ascent = v) } }
            else LabeledField("Bassin (m)", d.poolLength, Modifier.weight(1f)) { v -> set { it.copy(poolLength = v) } }
        }
        Text("Type d'effort réel", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
        Text("Un ressenti bas ne veut pas dire « facile » : choisis ce que tu as vraiment fait.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        val types = when {
            foot -> listOf("entraînement", "match")
            swim -> listOf("facile", "technique", "fractionné")
            else -> listOf("facile", "tempo", "fractionné", "course", "trail")
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { t ->
                FilterChip(selected = d.effortType == t, onClick = { set { it.copy(effortType = if (it.effortType == t) null else t) } },
                    label = { Text(t) })
            }
        }
    }
}

@Composable
private fun FeelingBlock(vm: LogViewModel, d: LogDraft) {
    SectionCard {
        Text("Ressenti global", style = MaterialTheme.typography.titleMedium)
        Text("1 = très facile, 10 = maximal", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..10).forEach { n ->
                val sel = d.rpe == n
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.edit { it.copy(rpe = if (it.rpe == n) null else n) } }
                        .semantics { contentDescription = "Ressenti $n sur 10" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", style = MaterialTheme.typography.labelLarge,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = LocalExtra.current.border)
        OutlinedTextField(d.notes, { v -> vm.edit { it.copy(notes = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Notes, douleurs, sensations") }, minLines = 2, shape = RoundedCornerShape(12.dp))
    }
}

@Composable
private fun NumField(value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onChange(v) },
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
private fun LabeledField(label: String, value: String, modifier: Modifier, decimal: Boolean = false, time: Boolean = false,
                         placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && (it == ',' || it == '.')) || (time && it == ':') }) onChange(v) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}


@Composable
private fun AddExerciseDialog(vm: LogViewModel, split: String?, onClose: () -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val catalogError by vm.catalogError.collectAsState()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var machine by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("reps") }
    var basis by remember { mutableStateOf("machine") }
    val muted = LocalExtra.current.muted

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (creating) "Nouvel exercice" else "Ajouter un exercice") },
        text = {
            if (!creating) Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Rechercher (curl, tirage…)") }, shape = RoundedCornerShape(12.dp))
                catalogError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp)) }
                val list = catalog.orEmpty().filter {
                    query.isBlank() || it.name.contains(query, true) || (it.equipmentVariant ?: "").contains(query, true)
                }.sortedWith(compareBy({ it.split != split }, { it.name }))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                    if (catalog == null && catalogError == null) item { Text("Chargement…", color = muted) }
                    items(list.size) { idx ->
                        val v = list[idx]
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { vm.addKnownExercise(v); onClose() }.padding(vertical = 10.dp, horizontal = 6.dp)) {
                            Text(v.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(v.equipmentVariant, Fmt.splitLabel(v.split)).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                    }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Exercice") }, placeholder = { Text("ex. Shrugs") }, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(machine, { machine = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Machine / matériel") }, placeholder = { Text("ex. Smith, poulie, haltères") }, shape = RoundedCornerShape(12.dp))
                Text("On compte en", style = MaterialTheme.typography.labelMedium, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("reps" to "Répétitions", "steps" to "Pas", "seconds" to "Secondes").forEach { (k, l) ->
                        FilterChip(selected = unit == k, onClick = { unit = k }, label = { Text(l) })
                    }
                }
                Text("La charge est", style = MaterialTheme.typography.labelMedium, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("machine" to "Machine", "total" to "Barre", "per_hand" to "Par main", "bodyweight" to "Corps").forEach { (k, l) ->
                        FilterChip(selected = basis == k, onClick = { basis = k }, label = { Text(l) })
                    }
                }
                Text("Une nouvelle machine n'est jamais comparée aux autres.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        },
        confirmButton = {
            if (creating) Button(enabled = name.isNotBlank(), onClick = {
                vm.addNewExercise(name, machine, unit, basis, split); onClose()
            }) { Text("Ajouter") }
            else TextButton(onClick = { creating = true }) { Text("Exercice absent de la liste") }
        },
        dismissButton = { TextButton(onClick = { if (creating) creating = false else onClose() }) { Text(if (creating) "Retour" else "Fermer") } },
    )
}


/**
 * Ouvre une conversation Claude avec un message prérempli (séance en cours + question).
 * Le message est aussi copié : si l'appli Claude ignore le préremplissage, il suffit de le coller.
 */
private fun askCoach(ctx: Context, message: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Message pour le coach", message))
    Toast.makeText(ctx, "Séance copiée : colle-la dans Claude si elle n'apparaît pas", Toast.LENGTH_LONG).show()
    val url = "https://claude.ai/new?q=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
