package com.tepmex.vibealphabet.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.formatSegments
import com.tepmex.vibealphabet.core.waveformFor
import com.tepmex.vibealphabet.haptics.PlayResult
import com.tepmex.vibealphabet.ui.LocalGraph
import com.tepmex.vibealphabet.ui.components.WaveformStrip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LearnScreen() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<SignalId?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val job = remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { job.value?.cancel() }
    }

    fun play(signal: SignalId) {
        job.value?.cancel()
        graph.player.stop()
        val waveform = waveformFor(signal, graph.windowMs)
        when (val result = graph.player.play(waveform)) {
            PlayResult.Played -> {
                notice = null
                playing = signal
                job.value = scope.launch {
                    delay(waveform.totalMs)
                    if (playing == signal) playing = null
                }
            }
            PlayResult.MissingVibrator -> {
                playing = null
                notice = "На этом устройстве нет вибромотора."
            }
            is PlayResult.Rejected -> {
                playing = null
                notice = result.message
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Обучение", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Нажмите сигнал. Окно ${graph.windowMs} мс — это команда целиком, вместе с тишиной.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!graph.player.hasVibrator) {
            Notice("На этом устройстве нет вибромотора.")
        } else if (!graph.player.hasAmplitudeControl) {
            Notice("Нет управления амплитудой: слабый и сильный сожмутся в одну силу.")
        }
        notice?.let { Notice(it) }
        SignalId.entries.forEach { signal ->
            val waveform = waveformFor(signal, graph.windowMs)
            val active = playing == signal
            Surface(
                onClick = { play(signal) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(signal.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    WaveformStrip(
                        waveform = waveform,
                        active = active,
                        onColor = MaterialTheme.colorScheme.primary,
                        offColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                    Text(
                        formatSegments(waveform),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
