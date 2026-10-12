package com.tepmex.hapticlab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFFE39B2B),
    onPrimary = Color(0xFF2A1C00),
    secondary = Color(0xFFB7C4A8),
    background = Color(0xFF12140F),
    surface = Color(0xFF1C1F18),
    surfaceVariant = Color(0xFF2A2E26),
    onSurface = Color(0xFFF3F1E8),
    onSurfaceVariant = Color(0xFFC9C6BA),
)

private val Type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HapticLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
