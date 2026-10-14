package com.tepmex.vibealphabet.ui.test

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.TestMachine
import com.tepmex.vibealphabet.core.TestPhase
import com.tepmex.vibealphabet.core.waveformFor
import com.tepmex.vibealphabet.haptics.PlayResult
import com.tepmex.vibealphabet.ui.AppGraph
import com.tepmex.vibealphabet.ui.LocalGraph
import com.tepmex.vibealphabet.ui.theme.CorrectGreen
import com.tepmex.vibealphabet.ui.theme.MissRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestViewModel(private val graph: AppGraph) : ViewModel() {
    private val machine = TestMachine()
    var phase by mutableStateOf<TestPhase>(TestPhase.Idle)
        private set
    var playing by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    private var playJob: Job? = null

    fun onBlankPressed() {
        val signal = machine.onBlankPressed()
        phase = machine.phase
        play(signal)
    }

    fun onChoice(choice: SignalId) {
        if (machine.phase !is TestPhase.Prompt) return
        playJob?.cancel()
        graph.player.stop()
        playing = false
        machine.answer(choice)
        phase = machine.phase
    }

    private fun play(signal: SignalId) {
        playJob?.cancel()
        graph.player.stop()
        val waveform = waveformFor(signal, graph.windowMs)
        when (val result = graph.player.play(waveform)) {
            PlayResult.Played -> {
                notice = null
                playing = true
                playJob = viewModelScope.launch {
                    delay(waveform.totalMs)
                    playing = false
                }
            }
            PlayResult.MissingVibrator -> {
                playing = false
                notice = "На этом устройстве нет вибромотора."
            }
            is PlayResult.Rejected -> {
                playing = false
                notice = result.message
            }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
        graph.player.stop()
    }
}

@Composable
fun TestScreen(model: TestViewModel = testViewModel()) {
    val phase = model.phase
    val scale by animateFloatAsState(
        targetValue = if (model.playing) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "blank-button",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Тест",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            instruction(phase),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (phase is TestPhase.Result) {
            Verdict(phase)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = model::onBlankPressed,
            modifier = Modifier
                .size(156.dp)
                .scale(scale)
                .semantics { contentDescription = "Воспроизвести сигнал" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {}
        model.notice?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        when (val current = phase) {
            TestPhase.Idle -> Unit
            is TestPhase.Prompt -> {
                Text(
                    "Какой это сигнал?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                SignalId.entries.forEach { signal ->
                    ChoiceButton(
                        title = signal.title,
                        border = null,
                        onClick = { model.onChoice(signal) },
                    )
                }
            }
            is TestPhase.Result -> {
                SignalId.entries.forEach { signal ->
                    val border = when {
                        signal == current.signal -> BorderStroke(2.dp, CorrectGreen)
                        signal == current.choice -> BorderStroke(2.dp, MissRed)
                        else -> null
                    }
                    ChoiceButton(title = signal.title, border = border, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun Verdict(result: TestPhase.Result) {
    Text(
        if (result.correct) "Верно" else "Неверно",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (result.correct) CorrectGreen else MissRed,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Это был сигнал «${result.signal.title}»: ${result.signal.patternName}. ${result.signal.sensation}.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!result.correct) {
        Text(
            "Вы выбрали «${result.choice.title}»: ${result.choice.patternName}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChoiceButton(title: String, border: BorderStroke?, onClick: (() -> Unit)?) {
    val modifier = Modifier.fillMaxWidth()
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceVariant
    val label = @Composable {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = label)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            content = label,
        )
    }
}

private fun instruction(phase: TestPhase): String = when (phase) {
    TestPhase.Idle -> "Нажмите круглую кнопку. На ней нет подписи: прозвучит случайный сигнал."
    is TestPhase.Prompt -> "Кнопка без подписи повторяет тот же сигнал."
    is TestPhase.Result -> "Нажмите кнопку ещё раз — испытание начнётся заново."
}

@Composable
private fun testViewModel(): TestViewModel {
    val graph = LocalGraph.current
    return viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TestViewModel(graph) as T
            }
        },
    )
}
