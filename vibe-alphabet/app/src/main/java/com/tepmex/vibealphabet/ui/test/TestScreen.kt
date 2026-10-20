package com.tepmex.vibealphabet.ui.test

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.vibealphabet.core.DrillMachine
import com.tepmex.vibealphabet.core.DrillPhase
import com.tepmex.vibealphabet.core.DrillSubject
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.SymbolBook
import com.tepmex.vibealphabet.core.Waveform
import com.tepmex.vibealphabet.core.formatSymbolSignals
import com.tepmex.vibealphabet.haptics.PlayResult
import com.tepmex.vibealphabet.ui.AppGraph
import com.tepmex.vibealphabet.ui.LocalGraph
import com.tepmex.vibealphabet.ui.theme.CorrectGreen
import com.tepmex.vibealphabet.ui.theme.MissRed
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestViewModel(private val graph: AppGraph) : ViewModel() {
    private val random = Random.Default
    private val signalDrill = DrillMachine {
        SignalId.entries[random.nextInt(SignalId.entries.size)]
    }
    private val symbolDrill = DrillMachine {
        val pool = graph.symbols.symbols
        check(pool.size >= 2) { "Для тренировки символов нужно хотя бы два" }
        pool[random.nextInt(pool.size)].id
    }

    var signalPhase by mutableStateOf<DrillPhase<SignalId>>(DrillPhase.Idle)
        private set
    var symbolPhase by mutableStateOf<DrillPhase<String>>(DrillPhase.Idle)
        private set
    var playing by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    private var playJob: Job? = null

    fun setSubject(subject: DrillSubject) {
        if (subject == graph.drillSubject) return
        stopPlayback()
        graph.selectDrill(subject)
    }

    fun onBlankPressed() {
        when (graph.drillSubject) {
            DrillSubject.Signals -> {
                val signal = signalDrill.onBlankPressed()
                signalPhase = signalDrill.phase
                play(graph.waveform(signal))
            }
            DrillSubject.Symbols -> {
                if (!graph.symbols.canTrain) return
                val id = symbolDrill.onBlankPressed()
                symbolPhase = symbolDrill.phase
                val symbol = graph.symbols.symbol(id) ?: return
                play(graph.symbolWaveform(symbol))
            }
        }
    }

    fun onSignalChoice(choice: SignalId) {
        if (graph.drillSubject != DrillSubject.Signals) return
        if (signalDrill.phase !is DrillPhase.Prompt) return
        stopPlayback()
        signalDrill.answer(choice)
        signalPhase = signalDrill.phase
    }

    fun onSymbolChoice(choiceId: String) {
        if (graph.drillSubject != DrillSubject.Symbols) return
        if (symbolDrill.phase !is DrillPhase.Prompt) return
        stopPlayback()
        symbolDrill.answer(choiceId)
        symbolPhase = symbolDrill.phase
    }

    fun syncSymbols() {
        val ids = graph.symbols.symbols.map { it.id }.toSet()
        val phase = symbolDrill.phase
        val invalid = when (phase) {
            DrillPhase.Idle -> false
            is DrillPhase.Prompt -> phase.item !in ids || !graph.symbols.canTrain
            is DrillPhase.Result -> phase.item !in ids || phase.choice !in ids || !graph.symbols.canTrain
        }
        if (!invalid) return
        if (graph.drillSubject == DrillSubject.Symbols) stopPlayback()
        symbolDrill.reset()
        symbolPhase = DrillPhase.Idle
    }

    private fun play(waveform: Waveform) {
        playJob?.cancel()
        graph.player.stop()
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

    private fun stopPlayback() {
        playJob?.cancel()
        graph.player.stop()
        playing = false
    }

    override fun onCleared() {
        playJob?.cancel()
        graph.player.stop()
    }
}

@Composable
fun TestScreen(model: TestViewModel = testViewModel()) {
    val graph = LocalGraph.current
    val subject = graph.drillSubject
    SideEffect { model.syncSymbols() }
    val scale by animateFloatAsState(
        targetValue = if (model.playing) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "blank-button",
    )
    val canPress = subject == DrillSubject.Signals || graph.symbols.canTrain
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
            "Что тренировать",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        SubjectSwitch(subject = subject, onSelect = model::setSubject)
        Text(
            instruction(subject, model.signalPhase, model.symbolPhase, graph.symbols.canTrain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subject == DrillSubject.Signals && model.signalPhase is DrillPhase.Result) {
            SignalVerdict(model.signalPhase as DrillPhase.Result<SignalId>)
        }
        if (subject == DrillSubject.Symbols && model.symbolPhase is DrillPhase.Result) {
            SymbolVerdict(model.symbolPhase as DrillPhase.Result<String>, graph.symbols)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = model::onBlankPressed,
            enabled = canPress,
            modifier = Modifier
                .size(156.dp)
                .scale(scale)
                .semantics { contentDescription = blankDescription(subject, canPress) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {}
        model.notice?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        when (subject) {
            DrillSubject.Signals -> SignalChoices(model)
            DrillSubject.Symbols -> SymbolChoices(model, graph.symbols)
        }
    }
}

@Composable
private fun SubjectSwitch(subject: DrillSubject, onSelect: (DrillSubject) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubjectButton(
            label = "Сигналы",
            selected = subject == DrillSubject.Signals,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DrillSubject.Signals) },
        )
        SubjectButton(
            label = "Символы",
            selected = subject == DrillSubject.Symbols,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DrillSubject.Symbols) },
        )
    }
}

@Composable
private fun SubjectButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SignalChoices(model: TestViewModel) {
    when (val current = model.signalPhase) {
        DrillPhase.Idle -> Unit
        is DrillPhase.Prompt -> {
            Text(
                "Какой это сигнал?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            SignalId.entries.forEach { signal ->
                ChoiceButton(title = signal.title, border = null, onClick = { model.onSignalChoice(signal) })
            }
        }
        is DrillPhase.Result -> {
            SignalId.entries.forEach { signal ->
                val border = when {
                    signal == current.item -> BorderStroke(2.dp, CorrectGreen)
                    signal == current.choice -> BorderStroke(2.dp, MissRed)
                    else -> null
                }
                ChoiceButton(title = signal.title, border = border, onClick = null)
            }
        }
    }
}

@Composable
private fun SymbolChoices(model: TestViewModel, book: SymbolBook) {
    if (!book.canTrain) return
    when (val current = model.symbolPhase) {
        DrillPhase.Idle -> Unit
        is DrillPhase.Prompt -> {
            Text(
                "Какой это символ?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            book.symbols.forEach { symbol ->
                ChoiceButton(
                    title = symbol.spelling,
                    border = null,
                    onClick = { model.onSymbolChoice(symbol.id) },
                )
            }
        }
        is DrillPhase.Result -> {
            book.symbols.forEach { symbol ->
                val border = when {
                    symbol.id == current.item -> BorderStroke(2.dp, CorrectGreen)
                    symbol.id == current.choice -> BorderStroke(2.dp, MissRed)
                    else -> null
                }
                ChoiceButton(title = symbol.spelling, border = border, onClick = null)
            }
        }
    }
}

@Composable
private fun SignalVerdict(result: DrillPhase.Result<SignalId>) {
    Text(
        if (result.correct) "Верно" else "Неверно",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (result.correct) CorrectGreen else MissRed,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Это был сигнал «${result.item.title}»: ${result.item.patternName}. ${result.item.sensation}.",
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
private fun SymbolVerdict(result: DrillPhase.Result<String>, book: SymbolBook) {
    val shown = book.symbol(result.item) ?: return
    val chosen = book.symbol(result.choice) ?: return
    Text(
        if (result.correct) "Верно" else "Неверно",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (result.correct) CorrectGreen else MissRed,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Это было «${shown.spelling}»: ${formatSymbolSignals(shown.signals)}.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!result.correct) {
        Text(
            "Вы выбрали «${chosen.spelling}»: ${formatSymbolSignals(chosen.signals)}.",
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

private fun instruction(
    subject: DrillSubject,
    signalPhase: DrillPhase<SignalId>,
    symbolPhase: DrillPhase<String>,
    canTrainSymbols: Boolean,
): String {
    if (subject == DrillSubject.Symbols && !canTrainSymbols) {
        return "Соберите хотя бы два символа в редакторе. Тогда здесь можно тренировать их вслепую."
    }
    return if (subject == DrillSubject.Signals) {
        when (signalPhase) {
            DrillPhase.Idle -> "Нажмите круглую кнопку. На ней нет подписи: прозвучит случайный сигнал."
            is DrillPhase.Prompt -> "Кнопка без подписи повторяет тот же сигнал."
            is DrillPhase.Result -> "Нажмите кнопку ещё раз — испытание начнётся заново."
        }
    } else {
        when (symbolPhase) {
            DrillPhase.Idle -> "Нажмите круглую кнопку. На ней нет подписи: прозвучит случайный символ, сигнал за сигналом."
            is DrillPhase.Prompt -> "Кнопка без подписи повторяет тот же символ."
            is DrillPhase.Result -> "Нажмите кнопку ещё раз — испытание начнётся заново."
        }
    }
}

private fun blankDescription(subject: DrillSubject, canPress: Boolean): String = when {
    subject == DrillSubject.Symbols && !canPress -> "Сначала соберите два символа"
    subject == DrillSubject.Symbols -> "Воспроизвести символ"
    else -> "Воспроизвести сигнал"
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
