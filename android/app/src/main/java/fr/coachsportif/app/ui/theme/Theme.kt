package fr.coachsportif.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Sledding
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Direction : « couloirs ». Chaque sport a sa couleur de couloir (piste, bassin, pelouse, fonte),
 * reprise en liseré à gauche des cartes et en pastilles. Le reste reste sobre : fond calcaire froid,
 * encre bleu nuit, surfaces plates bordées plutôt qu'ombrées. Les chiffres sont en chasse fixe (tnum)
 * pour que charges, allures et temps s'alignent.
 */

val Ink = Color(0xFF14212B)
val Chalk = Color(0xFFF2F4F3)
val Pine = Color(0xFF0F6B57)
val Track = Color(0xFFD2452F)      // course : tartan
val Pool = Color(0xFF1F7FB8)       // natation : bassin
val Pitch = Color(0xFF2E8B46)      // football : pelouse
val Iron = Color(0xFF6A8196)       // musculation : fonte
val Sled = Color(0xFFC98A1B)       // HYROX : traîneau
val Trail = Color(0xFF8A5A35)
val Amber = Color(0xFFE5A23A)
val Ember = Color(0xFFC0392B)

@Immutable
data class SportStyle(val color: Color, val emoji: String, val label: String, val icon: ImageVector)

fun sportStyle(sport: String, subtype: String? = null): SportStyle = when (sport) {
    "running" -> if (subtype == "trail") SportStyle(Trail, "⛰️", "Trail", Icons.Filled.Hiking)
                 else SportStyle(Track, "🏃", "Course", Icons.AutoMirrored.Filled.DirectionsRun)
    "swimming" -> SportStyle(Pool, "🏊", "Natation", Icons.Filled.Pool)
    "strength" -> SportStyle(Iron, "💪", "Musculation", Icons.Filled.FitnessCenter)
    "football" -> SportStyle(Pitch, "⚽", "Football", Icons.Filled.SportsSoccer)
    "hyrox" -> SportStyle(Sled, "🛷", "HYROX", Icons.Filled.Sledding)
    "cycling" -> SportStyle(Pool, "🚴", "Vélo", Icons.AutoMirrored.Filled.DirectionsBike)
    "walking" -> SportStyle(Iron, "🚶", "Marche", Icons.AutoMirrored.Filled.DirectionsWalk)
    else -> SportStyle(Iron, "🏅", "Activité", Icons.Filled.EmojiEvents)
}

@Immutable
data class Extra(val border: Color, val muted: Color, val chipBg: Color)

val LocalExtra = staticCompositionLocalOf { Extra(Color(0xFFDCE1E4), Color(0xFF5E6B76), Color(0xFFE6EAEC)) }

private val Light = lightColorScheme(
    primary = Pine, onPrimary = Color.White, primaryContainer = Color(0xFFD4ECE4), onPrimaryContainer = Color(0xFF06372C),
    secondary = Track, tertiary = Amber, error = Ember,
    background = Chalk, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE8ECEE), onSurfaceVariant = Color(0xFF5E6B76), outline = Color(0xFFC7CED3),
    outlineVariant = Color(0xFFDCE1E4),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6FD3B4), onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF0F4F41), onPrimaryContainer = Color(0xFFCFF3E7),
    secondary = Color(0xFFFF8A73), tertiary = Amber, error = Color(0xFFFF8A80),
    background = Color(0xFF0E1419), onBackground = Color(0xFFE4E9EC), surface = Color(0xFF162029), onSurface = Color(0xFFE4E9EC),
    surfaceVariant = Color(0xFF1F2B35), onSurfaceVariant = Color(0xFF9FAEB9), outline = Color(0xFF3A4955),
    outlineVariant = Color(0xFF2A3843),
)

private val tnum = "tnum"
private val Type = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFeatureSettings = tnum),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFeatureSettings = tnum),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFeatureSettings = tnum),
)

/** Chiffres de performance : gros, gras, chasse fixe. */
val NumberLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum")
val NumberMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum")

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val extra = if (dark) Extra(Color(0xFF2A3843), Color(0xFF9FAEB9), Color(0xFF22303A))
                else Extra(Color(0xFFDCE1E4), Color(0xFF5E6B76), Color(0xFFE8ECEE))
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}
