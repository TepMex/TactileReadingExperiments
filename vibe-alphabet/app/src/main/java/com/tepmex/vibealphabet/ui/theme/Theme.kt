package com.tepmex.vibealphabet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF0E1418)
internal val CorrectGreen = Color(0xFF8EE0A8)
internal val MissRed = Color(0xFFFF8B7A)

private val Colors = darkColorScheme(
    primary = Color(0xFF3DDCB4),
    onPrimary = Color(0xFF06241C),
    secondary = Color(0xFF8FB8C4),
    onSecondary = Color(0xFF0E242C),
    tertiary = CorrectGreen,
    onTertiary = Color(0xFF063018),
    background = Ink,
    onBackground = Color(0xFFE7F0F2),
    surface = Color(0xFF172228),
    onSurface = Color(0xFFE7F0F2),
    surfaceVariant = Color(0xFF213038),
    onSurfaceVariant = Color(0xFFA9BCC2),
    error = MissRed,
    onError = Color(0xFF2A0C08),
    outline = Color(0xFF3A525A),
)

private val Type = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun VibeAlphabetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
