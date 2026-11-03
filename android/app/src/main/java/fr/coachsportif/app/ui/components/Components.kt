package fr.coachsportif.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.local.OutboxOp
import fr.coachsportif.app.domain.CompletedActivity
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.ui.theme.Amber
import fr.coachsportif.app.ui.theme.Ember
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.NumberMedium
import fr.coachsportif.app.ui.theme.SportStyle
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

/* ------------------------------------------------------------------ statut */

@Composable
fun StatusChip(status: String) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg, dot) = when (status) {
        "completed" -> Triple(cs.primary, cs.onPrimary, cs.onPrimary)
        "pending_confirmation" -> Triple(Amber.copy(alpha = 0.22f), cs.onSurface, Amber)
        "in_progress" -> Triple(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        "cancelled", "skipped" -> Triple(LocalExtra.current.chipBg, LocalExtra.current.muted, LocalExtra.current.muted)
        else -> Triple(LocalExtra.current.chipBg, cs.onSurface, cs.onSurfaceVariant)
    }
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = "Statut : ${Fmt.statusLabel(status)}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == "completed") Text("✓ ", color = fg, style = MaterialTheme.typography.labelMedium)
        else Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        if (status != "completed") Spacer(Modifier.width(6.dp))
        Text(Fmt.statusLabel(status), color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/* ------------------------------------------------------------------ synchro */

/** Une ligne discrète quand tout va bien ; un bandeau coloré seulement quand il faut agir. */
@Composable
fun SyncLine(fetchedAtMs: Long?, planVersion: Int?, error: String?, outbox: List<OutboxOp>, modifier: Modifier = Modifier) {
    val fmt = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRENCH).withZone(Fmt.PARIS)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            when {
                fetchedAtMs == null -> "Pas encore synchronisé sur ce téléphone"
                else -> "Synchronisé le ${fmt.format(Instant.ofEpochMilli(fetchedAtMs))}" + (planVersion?.let { ", planning v$it" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        )
        if (error != null) Banner(Ember, "Hors connexion : $error. Tu vois le dernier état enregistré sur le téléphone.")
        val pending = outbox.count { it.state == "pending" }
        if (pending > 0) Banner(Amber, "$pending séance(s) enregistrée(s) sur le téléphone, envoi dès que le réseau revient.")
        outbox.filter { it.state == "failed" }.forEach { Banner(Ember, "${it.label} refusée par le serveur (${it.lastError}).") }
    }
}

@Composable
fun Banner(color: Color, text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(12.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/* ------------------------------------------------------------------ cartes */

/** Carte « couloir » : liseré de la couleur du sport à gauche. */
@Composable
fun LaneCard(color: Color, modifier: Modifier = Modifier, dimmed: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtra.current.border),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (dimmed) color.copy(alpha = 0.35f) else color))
            Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), content = content)
        }
    }
}

/** Carte neutre, pour les blocs qui ne sont pas liés à un sport. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtra.current.border),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun SportBadge(style: SportStyle, size: Int = 44, filled: Boolean = false) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (filled) style.color else style.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(style.icon, contentDescription = style.label,
            tint = if (filled) Color.White else style.color, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Encart de référence (dernière séance, objectif…) : fond légèrement teinté, sans bordure. */
@Composable
fun InfoBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun ScreenHeader(kicker: String?, title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        kicker?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp))
}

/* ------------------------------------------------------------------ chiffres */

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = NumberMedium, maxLines = 1, overflow = TextOverflow.Clip)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
    }
}

/** Rangée de 2 à 4 mesures alignées ; les valeurs inconnues ne sont pas affichées (jamais « 0 »). */
@Composable
fun MetricsRow(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Row(modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.take(4).forEach { (v, l) -> Metric(v, l, Modifier.weight(1f)) }
    }
}

@Composable
fun KeyValue(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Résultat réel : mesures en grand, séries par exercice, écart prévu/réalisé signalé. */
@Composable
fun ResultSummary(r: CompletedActivity, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        r.endurance?.let { e ->
            MetricsRow(listOfNotNull(
                Fmt.km(e.distanceM)?.let { it to "distance" },
                Fmt.duration(e.durationS)?.let { it to "durée" },
                Fmt.pace(e.paceSPerKm)?.removeSuffix("/km")?.let { it to "min/km" },
                e.avgHr?.let { "$it" to "FC moy." },
            ))
        }
        val extra = listOfNotNull(
            r.rpe10?.let { "ressenti ${Fmt.num(it)}/10" },
            r.endurance?.ascentM?.let { "+$it m D+" },
            r.endurance?.maxHr?.let { "FC max $it" },
            r.workSetsCount?.let { "$it séries de travail" },
        )
        if (extra.isNotEmpty()) Text(extra.joinToString("   "), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        if (r.plannedSessionType != null && r.actualSessionType != null && r.plannedSessionType != r.actualSessionType)
            Banner(Amber, "Prévu « ${r.plannedSessionType} », réalisé « ${r.actualSessionType} ».")
        if (!compact) r.exercises.forEach { ex ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(ex.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Text(Fmt.sets(ex.workSets, ex.loadBasis) + (ex.equipmentVariant?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
        }
        Text(sourceLabel(r.source), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted,
            modifier = Modifier.padding(top = 4.dp))
    }
}

fun sourceLabel(s: String) = when (s) {
    "app_logged" -> "Saisi dans l'application"
    "user_report" -> "Déclaré au coach"
    "historical_dossier" -> "Dossier historique"
    "garmin_import" -> "Import Garmin"
    else -> s
}

/** Barre de progression segmentée (une case par séance du jour). */
@Composable
fun SegmentBar(colors: List<Pair<Color, Boolean>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { (c, done) ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                .background(if (done) c else c.copy(alpha = 0.18f))
                .border(0.dp, Color.Transparent, CircleShape))
        }
    }
}
