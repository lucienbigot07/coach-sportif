package fr.coachsportif.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.BuildConfig
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.ui.components.ScreenHeader
import fr.coachsportif.app.ui.components.SectionCard
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.today.describe
import kotlinx.coroutines.launch

private const val CONFIRM = "SUPPRIMER TOUTES MES DONNEES"

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as CoachApp).repository
    val session by repo.session.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val danger = MaterialTheme.colorScheme.error

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val json = repo.exportAll()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                "Export enregistré."
            }.getOrElse { "Export impossible : ${describe(it)}" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(kicker = null, title = "Réglages")
        SectionCard {
            SettingRow(Icons.Filled.SystemUpdate, "Mettre à jour l'application",
                "Version installée : ${BuildConfig.VERSION_NAME}. Télécharge la dernière version puis ouvre le fichier.",
                onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.UPDATE_URL))) })
        }
        SectionCard {
            SettingRow(Icons.Filled.Person, session?.email ?: "Non connecté", "Compte relié au connecteur Claude")
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LocalExtra.current.border)
            SettingRow(Icons.Filled.Lock, "Données privées", "Stockées dans ta base Supabase, lisibles uniquement avec ton compte. " +
                "Claude y accède seulement via le connecteur que tu as autorisé.")
        }
        SectionCard {
            SettingRow(Icons.Filled.Download, "Exporter mes données", "Fichier JSON complet", onClick = { exportLauncher.launch("coach-sportif-export.json") })
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LocalExtra.current.border)
            SettingRow(Icons.AutoMirrored.Filled.Logout, "Se déconnecter", "Efface le cache du téléphone, pas tes données", onClick = { scope.launch { repo.signOut() } })
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LocalExtra.current.border)
            SettingRow(Icons.Filled.DeleteForever, "Supprimer toutes mes données", "Séances, plannings et historique, définitivement",
                tint = danger, onClick = { deleting = true })
        }
        message?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        Text("Fuseau Europe/Paris, unités kg, km et bpm.", style = MaterialTheme.typography.bodySmall,
            color = LocalExtra.current.muted, modifier = Modifier.padding(20.dp))
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Supprimer définitivement ?") },
        text = {
            Column {
                Text("Toutes tes séances, plannings et historiques seront effacés. Pour confirmer, tape :")
                Text(CONFIRM, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(typed, { typed = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = typed == CONFIRM, onClick = {
                deleting = false
                scope.launch {
                    message = runCatching { repo.deleteAllData(CONFIRM); "Données supprimées." }.getOrElse { "Échec : ${describe(it)}" }
                }
            }) { Text("Supprimer", color = danger) }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, tint: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint ?: MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
    }
}
