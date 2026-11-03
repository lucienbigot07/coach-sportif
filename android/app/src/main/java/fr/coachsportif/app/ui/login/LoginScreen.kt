package fr.coachsportif.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.data.remote.ApiException
import fr.coachsportif.app.ui.components.Banner
import fr.coachsportif.app.ui.theme.Iron
import fr.coachsportif.app.ui.theme.LocalExtra
import fr.coachsportif.app.ui.theme.Pitch
import fr.coachsportif.app.ui.theme.Pool
import fr.coachsportif.app.ui.theme.Track
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

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(32.dp))
        // les quatre couloirs de l'appli : course, natation, foot, muscu
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Track, Pool, Pitch, Iron).forEach { c -> Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c)) }
        }
        Text("Coach Sportif", style = MaterialTheme.typography.displaySmall)
        Text("Ton programme validé avec le coach, tes séances saisies ici.", style = MaterialTheme.typography.bodyLarge,
            color = LocalExtra.current.muted)
        Spacer(Modifier.height(8.dp))
        if (!repo.isConfigured) Banner(MaterialTheme.colorScheme.error, "Application non configurée : adresse de la base manquante.")
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Mot de passe") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Banner(MaterialTheme.colorScheme.error, it) }
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank() && repo.isConfigured,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repo.signIn(email, password) }.onFailure {
                        error = if (it is ApiException && it.status in 400..401) "E-mail ou mot de passe incorrect. Utilise l'utilisateur créé dans Supabase."
                                else "Connexion impossible : ${describe(it)}"
                    }
                    busy = false
                }
            },
        ) { Text(if (busy) "Connexion…" else "Se connecter") }
        Text("Même compte que celui utilisé pour connecter Claude.", style = MaterialTheme.typography.bodySmall,
            color = LocalExtra.current.muted)
    }
}
