package fr.coachsportif.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Pine = Color(0xFF0B6E4F)
val Ember = Color(0xFFE4572E)
val Amber = Color(0xFFF2A541)
val Slate = Color(0xFF5B6770)

private val Light = lightColorScheme(
    primary = Pine, onPrimary = Color.White, secondary = Ember, tertiary = Amber,
    background = Color(0xFFF6F7F9), surface = Color.White, surfaceVariant = Color(0xFFEDEFF2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5FD3A6), onPrimary = Color(0xFF00382A), secondary = Color(0xFFFF8A65), tertiary = Amber,
    background = Color(0xFF0F1214), surface = Color(0xFF171B1E), surfaceVariant = Color(0xFF22282C),
)

private val Type = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
