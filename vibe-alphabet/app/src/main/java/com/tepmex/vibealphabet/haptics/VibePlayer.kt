package com.tepmex.vibealphabet.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.tepmex.vibealphabet.core.Waveform

sealed interface PlayResult {
    data object Played : PlayResult

    data object MissingVibrator : PlayResult

    data class Rejected(val message: String) : PlayResult
}

class VibePlayer(context: Context) {
    private val vibrator: Vibrator? =
        context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator

    val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    val hasAmplitudeControl: Boolean
        get() = hasVibrator && vibrator?.hasAmplitudeControl() == true

    fun play(waveform: Waveform): PlayResult {
        val vibrator = vibrator
        if (vibrator == null || !vibrator.hasVibrator()) return PlayResult.MissingVibrator
        return try {
            Log.i(TAG, "waveform timings=${waveform.timingsMs} amplitudes=${waveform.amplitudes}")
            val effect = VibrationEffect.createWaveform(
                waveform.timingsMs.toLongArray(),
                waveform.amplitudes.toIntArray(),
                -1,
            )
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            PlayResult.Played
        } catch (error: RuntimeException) {
            PlayResult.Rejected(error.message ?: "Вибромотор отклонил команду")
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (error: RuntimeException) {
            Log.w(TAG, "cancel failed", error)
        }
    }

    private companion object {
        const val TAG = "VibeAlphabet"
    }
}
