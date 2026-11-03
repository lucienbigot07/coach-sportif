package fr.coachsportif.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.coachsportif.app.domain.PlannedActivity
import fr.coachsportif.app.ui.history.HistoryScreen
import fr.coachsportif.app.ui.log.LogScreen
import fr.coachsportif.app.ui.login.LoginScreen
import fr.coachsportif.app.ui.settings.SettingsScreen
import fr.coachsportif.app.ui.theme.CoachTheme
import fr.coachsportif.app.ui.today.TodayScreen
import fr.coachsportif.app.ui.week.WeekScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as CoachApp).repository
        setContent {
            CoachTheme {
                val session by repo.session.collectAsState()
                if (session == null) LoginScreen() else AppNav()
            }
        }
    }
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Dest("today", "Aujourd'hui", Icons.Filled.Today),
    Dest("week", "Semaine", Icons.Filled.CalendarMonth),
    Dest("history", "Progrès", Icons.Filled.Insights),
    Dest("settings", "Réglages", Icons.Filled.Settings),
)

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val start: (PlannedActivity) -> Unit = { a -> nav.navigate("log/${a.date}/${a.sessionKey}") }
    Scaffold(bottomBar = {
        if (tabs.any { it.route == route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { t ->
                NavigationBarItem(selected = route == t.route, label = { Text(t.label) }, icon = { Icon(t.icon, null) },
                    onClick = {
                        nav.navigate(t.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            NavHost(nav, startDestination = "today") {
                composable("today") { TodayScreen(onStart = start) }
                composable("week") { WeekScreen(onStart = start) }
                composable("history") { HistoryScreen() }
                composable("settings") { SettingsScreen() }
                composable("log/{date}/{key}") { b ->
                    LogScreen(b.arguments?.getString("date")!!, b.arguments?.getString("key")!!, onDone = { nav.popBackStack() })
                }
            }
        }
    }
}
