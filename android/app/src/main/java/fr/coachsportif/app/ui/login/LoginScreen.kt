package fr.coachsportif.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.data.remote.ApiException
import fr.coachsportif.app.ui.today.describe
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val repo = (LocalContext.current.applicationContext as CoachApp).repository
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🏃 Coach Sportif", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 48.dp))
        Text("Connecte-toi avec le même compte que celui autorisé pour le connecteur Claude.",
            style = MaterialTheme.typography.bodyMedium)
        if (!repo.isConfigured) Text("⚠️ Application non configurée : renseigner coach.supabaseUrl et coach.supabaseAnonKey " +
            "dans local.properties puis recompiler.", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Mot de passe") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank() && repo.isConfigured, modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repo.signIn(email, password) }.onFailure {
                        error = if (it is ApiException && it.status in 400..401) "Identifiants refusés." else "Connexion impossible : ${describe(it)}"
                    }
                    busy = false
                }
            }) { Text(if (busy) "Connexion…" else "Se connecter") }
    }
}
