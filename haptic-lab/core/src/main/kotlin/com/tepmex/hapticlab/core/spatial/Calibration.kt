package com.tepmex.hapticlab.core.spatial

/** Миллиметры на пиксель по системному xdpi. При негодном dpi используется 160. */
fun systemMmPerPx(xdpi: Float): Float {
    val dpi = if (xdpi.isFinite() && xdpi > 1f) xdpi else 160f
    return 25.4f / dpi
}

fun calibratedMmPerPx(xdpi: Float, scale: Float): Float {
    val safeScale = if (scale.isFinite() && scale > 0.05f) scale else 1f
    return systemMmPerPx(xdpi) * safeScale
}

/** scale = измеренная ширина / системная ширина. null, если ввод непригоден. */
fun scaleFromMeasuredWidth(systemWidthMm: Float, measuredWidthMm: Float): Float? {
    if (!systemWidthMm.isFinite() || !measuredWidthMm.isFinite()) return null
    if (systemWidthMm <= 0f || measuredWidthMm <= 0f) return null
    return measuredWidthMm / systemWidthMm
}

fun pxToMm(px: Float, mmPerPx: Float): Float = px * mmPerPx

fun mmToPx(mm: Float, mmPerPx: Float): Float {
    if (mmPerPx <= 0f) return 0f
    return mm / mmPerPx
}
