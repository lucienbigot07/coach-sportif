package fr.coachsportif.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.local.OutboxOp
import fr.coachsportif.app.domain.CompletedActivity
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.ui.theme.Amber
import fr.coachsportif.app.ui.theme.Ember
import fr.coachsportif.app.ui.theme.Pine
import fr.coachsportif.app.ui.theme.Slate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ViewModel construit avec le Repository de l'application (DI manuelle, sans framework). */
@Composable
inline fun <reified VM : ViewModel> repoViewModel(key: String? = null, crossinline create: (Repository) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as CoachApp).repository
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(repo) as T
    })
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "completed" -> Pine to Color.White
        "pending_confirmation" -> Amber to Color.Black
        "planned", "in_progress" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        "cancelled", "skipped" -> Slate to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Text(
        Fmt.statusLabel(status),
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "Statut : ${Fmt.statusLabel(status)}" },
    )
}

/** Date + version de la dernière synchronisation, et file d'attente. Jamais d'impression « à jour » trompeuse. */
@Composable
fun SyncBanner(fetchedAtMs: Long?, planVersion: Int?, error: String?, outbox: List<OutboxOp>) {
    val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRENCH).withZone(Fmt.PARIS)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        val sync = when {
            fetchedAtMs == null -> "⏳ Jamais synchronisé sur cet appareil"
            else -> "🔄 Synchronisé le ${fmt.format(Instant.ofEpochMilli(fetchedAtMs))}" +
                (planVersion?.let { " · planning v$it" } ?: "")
        }
        Text(sync, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text("⚠️ Non synchronisé : $error — données affichées = dernier état connu",
            color = Ember, style = MaterialTheme.typography.bodyMedium)
        val pending = outbox.count { it.state == "pending" }
        val failed = outbox.filter { it.state == "failed" }
        if (pending > 0) Text("📤 $pending saisie(s) en attente d'envoi (enregistrées sur le téléphone)",
            color = Amber, style = MaterialTheme.typography.bodyMedium)
        failed.forEach { Text("❌ ${it.label} refusé par le serveur : ${it.lastError}", color = Ember,
            style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun KeyValue(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Résumé d'un résultat réel (endurance et/ou muscu). */
@Composable
fun ResultSummary(r: CompletedActivity) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("✅ Réalisé", style = MaterialTheme.typography.titleMedium, color = Pine)
        r.endurance?.let { e ->
            val line = listOfNotNull(Fmt.km(e.distanceM), Fmt.duration(e.durationS), Fmt.pace(e.paceSPerKm),
                e.ascentM?.let { "+$it m" }, e.avgHr?.let { "FC $it" }, e.maxHr?.let { "max $it" }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge)
        }
        r.rpe10?.let { Text("Ressenti ${Fmt.num(it)}/10", style = MaterialTheme.typography.bodyMedium) }
        if (r.plannedSessionType != null && r.actualSessionType != null && r.plannedSessionType != r.actualSessionType)
            Text("⚠️ Prévu « ${r.plannedSessionType} », réalisé « ${r.actualSessionType} »", color = Ember,
                style = MaterialTheme.typography.bodyMedium)
        r.exercises.forEach { ex ->
            Text("• ${ex.name}${ex.equipmentVariant?.let { " ($it)" } ?: ""} : ${Fmt.sets(ex.workSets, ex.loadBasis)}",
                style = MaterialTheme.typography.bodyMedium)
        }
        r.workSetsCount?.let { Text("$it séries de travail", style = MaterialTheme.typography.bodyMedium) }
        Text("Source : ${sourceLabel(r.source)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun sourceLabel(s: String) = when (s) {
    "app_logged" -> "saisi dans l'application"
    "user_report" -> "déclaré au coach"
    "historical_dossier" -> "dossier historique"
    "garmin_import" -> "import Garmin"
    else -> s
}
