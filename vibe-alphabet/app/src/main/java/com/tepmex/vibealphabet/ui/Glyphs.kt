package com.tepmex.vibealphabet.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LearnGlyph: ImageVector = ImageVector.Builder(
    name = "Learn",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 10f)
        lineTo(6f, 10f)
        lineTo(6f, 14f)
        lineTo(3f, 14f)
        close()
        moveTo(8f, 7f)
        lineTo(13f, 7f)
        lineTo(13f, 17f)
        lineTo(8f, 17f)
        close()
        moveTo(15f, 10f)
        lineTo(17.5f, 10f)
        lineTo(17.5f, 14f)
        lineTo(15f, 14f)
        close()
        moveTo(19f, 10f)
        lineTo(21.5f, 10f)
        lineTo(21.5f, 14f)
        lineTo(19f, 14f)
        close()
    }
}.build()

val SymbolGlyph: ImageVector = ImageVector.Builder(
    name = "Symbols",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 4f)
        lineTo(11f, 4f)
        lineTo(11f, 12f)
        lineTo(3f, 12f)
        close()
        moveTo(13f, 4f)
        lineTo(21f, 4f)
        lineTo(21f, 12f)
        lineTo(13f, 12f)
        close()
        moveTo(8f, 14f)
        lineTo(16f, 14f)
        lineTo(16f, 22f)
        lineTo(8f, 22f)
        close()
    }
}.build()

val TestGlyph: ImageVector = ImageVector.Builder(
    name = "Test",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 4f)
        curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
        curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
        curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
        close()
    }
}.build()

val SettingsGlyph: ImageVector = ImageVector.Builder(
    name = "Settings",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        lineTo(20f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(4f, 15f)
        lineTo(20f, 15f)
        lineTo(20f, 17f)
        lineTo(4f, 17f)
        close()
        moveTo(8f, 5f)
        lineTo(12f, 5f)
        lineTo(12f, 11f)
        lineTo(8f, 11f)
        close()
        moveTo(13f, 13f)
        lineTo(17f, 13f)
        lineTo(17f, 19f)
        lineTo(13f, 19f)
        close()
    }
}.build()
