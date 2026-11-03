package fr.coachsportif.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.ui.components.SectionCard
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val json = repo.exportAll()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                "Export enregistré."
            }.getOrElse { "Export impossible : ${describe(it)}" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        SectionCard {
            Text("Compte", style = MaterialTheme.typography.titleMedium)
            Text(session?.email ?: "—")
            Text("Fuseau : Europe/Paris · Unités : kg, km, bpm", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard {
            Text("Confidentialité", style = MaterialTheme.typography.titleMedium)
            Text("Tes données (séances, FC, douleurs) sont stockées dans ta base Supabase, accessibles uniquement avec ton compte. " +
                "Claude y accède seulement via le connecteur que tu as autorisé.", style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("coach-sportif-export.json") }, Modifier.fillMaxWidth()) {
                    Text("Exporter toutes mes données (JSON)")
                }
                OutlinedButton(onClick = { scope.launch { repo.signOut() } }, Modifier.fillMaxWidth()) { Text("Se déconnecter") }
                Button(onClick = { deleting = true }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Supprimer toutes mes données")
                }
            }
            message?.let { Text(it, Modifier.padding(top = 8.dp)) }
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Suppression définitive") },
        text = {
            Column {
                Text("Toutes tes séances, plannings et historiques seront effacés. Tape « $CONFIRM » pour confirmer.")
                OutlinedTextField(typed, { typed = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = typed == CONFIRM, onClick = {
                deleting = false
                scope.launch {
                    message = runCatching { repo.deleteAllData(CONFIRM); "Données supprimées." }.getOrElse { "Échec : ${describe(it)}" }
                }
            }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } },
    )
}
