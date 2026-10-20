package com.tepmex.vibealphabet.ui.symbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.vibealphabet.core.MAX_SIGNALS_IN_SYMBOL
import com.tepmex.vibealphabet.core.MAX_SYMBOLS
import com.tepmex.vibealphabet.core.MAX_SYMBOL_LETTERS
import com.tepmex.vibealphabet.core.SignalId
import com.tepmex.vibealphabet.core.Symbol
import com.tepmex.vibealphabet.core.canonicalSpelling
import com.tepmex.vibealphabet.core.formatSymbolSignals
import com.tepmex.vibealphabet.core.spellingIssue
import com.tepmex.vibealphabet.haptics.PlayResult
import com.tepmex.vibealphabet.ui.LocalGraph
import com.tepmex.vibealphabet.ui.components.WaveformStrip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SymbolScreen() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val book = graph.symbols
    var playingId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val job = remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { job.value?.cancel() }
    }

    fun play(symbol: Symbol) {
        job.value?.cancel()
        graph.player.stop()
        val waveform = graph.symbolWaveform(symbol)
        when (val result = graph.player.play(waveform)) {
            PlayResult.Played -> {
                notice = null
                playingId = symbol.id
                job.value = scope.launch {
                    delay(waveform.totalMs)
                    if (playingId == symbol.id) playingId = null
                }
            }
            PlayResult.MissingVibrator -> {
                playingId = null
                notice = "На этом устройстве нет вибромотора."
            }
            is PlayResult.Rejected -> {
                playingId = null
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
        Text("Редактор символов", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Буква или буквосочетание собирается из сигналов обучения. Они играют подряд, каждый в своём окне ${graph.windowMs} мс. Один сигнал можно поставить несколько раз.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!graph.player.hasVibrator) {
            Notice("На этом устройстве нет вибромотора.")
        }
        notice?.let { Notice(it) }
        if (book.symbols.isEmpty()) {
            Text(
                "Пока нет символов. Добавьте букву или буквосочетание и соберите символ из сигналов.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val room = book.symbols.size < MAX_SYMBOLS
        WideButton(
            label = "Новый символ",
            enabled = room,
            onClick = { creating = true },
        )
        if (!room) {
            Text(
                "Можно сохранить не больше $MAX_SYMBOLS символов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        book.symbols.forEachIndexed { index, symbol ->
            SymbolCard(
                symbol = symbol,
                index = index,
                count = book.symbols.size,
                playing = playingId == symbol.id,
                editing = editingId == symbol.id,
                reveal = revealId == symbol.id,
                onRevealConsumed = { revealId = null },
                onPlay = { play(symbol) },
                onEdit = { editingId = if (editingId == symbol.id) null else symbol.id },
            )
        }
    }
    if (creating) {
        NewSymbolDialog(
            onDismiss = { creating = false },
            onCreate = { spelling, first ->
                val created = graph.addSymbol(spelling, first) ?: return@NewSymbolDialog false
                editingId = created.id
                revealId = created.id
                creating = false
                true
            },
        )
    }
}

@Composable
private fun SymbolCard(
    symbol: Symbol,
    index: Int,
    count: Int,
    playing: Boolean,
    editing: Boolean,
    reveal: Boolean,
    onRevealConsumed: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
) {
    val graph = LocalGraph.current
    val waveform = graph.symbolWaveform(symbol)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reveal) {
        if (reveal) {
            requester.bringIntoView()
            onRevealConsumed()
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester),
        shape = MaterialTheme.shapes.large,
        color = if (playing) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onPlay)
                    .semantics { contentDescription = "Воспроизвести символ ${symbol.spelling}" },
            ) {
                Text(symbol.spelling, style = MaterialTheme.typography.headlineSmall)
                Text(
                    formatSymbolSignals(symbol.signals),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                WaveformStrip(
                    waveform = waveform,
                    active = playing,
                    onColor = MaterialTheme.colorScheme.primary,
                    offColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
                Text(
                    "${symbol.signals.size} ${signalsWord(symbol.signals.size)} · ${waveform.totalMs} мс",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (editing) {
                SymbolEditor(symbol = symbol, index = index, count = count)
                Spacer(Modifier.height(8.dp))
            }
            WideButton(label = if (editing) "Готово" else "Изменить", enabled = true, onClick = onEdit)
        }
    }
}

@Composable
private fun SymbolEditor(symbol: Symbol, index: Int, count: Int) {
    val graph = LocalGraph.current
    var draft by remember(symbol.id) { mutableStateOf(symbol.spelling) }
    var error by remember(symbol.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(symbol.id) { mutableStateOf(false) }
    val atCap = symbol.signals.size >= MAX_SIGNALS_IN_SYMBOL

    fun applySpelling(raw: String) {
        val letters = raw.filter { it.isLetter() }.take(MAX_SYMBOL_LETTERS)
        val issue = when {
            letters.isEmpty() -> "Введите букву или буквосочетание."
            else -> spellingIssue(letters, graph.symbols, symbol.id)
        }
        if (issue == null) {
            graph.renameSymbol(symbol.id, letters)
            draft = canonicalSpelling(letters) ?: letters
            error = null
        } else {
            draft = letters
            error = issue
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = ::applySpelling,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            label = { Text("Буква или буквосочетание") },
            supportingText = {
                Text(
                    error ?: "До $MAX_SYMBOL_LETTERS букв. Регистр не важен.",
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { applySpelling(draft) }),
        )
        Text("Сигналы по порядку", style = MaterialTheme.typography.titleMedium)
        symbol.signals.forEachIndexed { signalIndex, signal ->
            SignalRow(
                position = signalIndex,
                total = symbol.signals.size,
                signal = signal,
                edited = graph.isEdited(signal),
                onUp = { graph.moveSymbolSignal(symbol.id, signalIndex, -1) },
                onDown = { graph.moveSymbolSignal(symbol.id, signalIndex, 1) },
                onRemove = { graph.removeSymbolSignalAt(symbol.id, signalIndex) },
            )
        }
        if (symbol.signals.size == 1) {
            Text(
                "Нужен хотя бы один сигнал. Добавьте второй, если символ длиннее одного окна.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (atCap) "В символе уже $MAX_SIGNALS_IN_SYMBOL сигналов." else "Добавить сигнал",
            style = MaterialTheme.typography.titleMedium,
        )
        SignalPicker(
            enabled = !atCap,
            selected = null,
            idleColor = MaterialTheme.colorScheme.surface,
            onPick = { graph.appendSymbolSignal(symbol.id, it) },
        )
        if (count > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Выше", enabled = index > 0) { graph.moveSymbol(symbol.id, -1) }
                SmallButton("Ниже", enabled = index < count - 1) { graph.moveSymbol(symbol.id, 1) }
            }
        }
        Surface(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                "Удалить",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${symbol.spelling}»?") },
            text = { Text("Цепочка сигналов этого символа исчезнет. Сами сигналы на обучении останутся.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        graph.removeSymbol(symbol.id)
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SignalRow(
    position: Int,
    total: Int,
    signal: SignalId,
    edited: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${signal.title} · ${signal.patternName}" + if (edited) " · изменён" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                signal.sensation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Раньше", enabled = position > 0, onClick = onUp)
                SmallButton("Позже", enabled = position < total - 1, onClick = onDown)
                SmallButton("Убрать", enabled = total > 1, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun NewSymbolDialog(
    onDismiss: () -> Unit,
    onCreate: (String, SignalId) -> Boolean,
) {
    val graph = LocalGraph.current
    var draft by remember { mutableStateOf("") }
    var first by remember { mutableStateOf(SignalId.SHORT) }
    var error by remember { mutableStateOf<String?>(null) }
    fun confirm() {
        val issue = spellingIssue(draft, graph.symbols, null)
        if (issue != null) {
            error = issue
            return
        }
        if (!onCreate(draft, first)) error = "Не удалось сохранить символ."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый символ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Надпись — буква или буквосочетание. Первый сигнал можно сразу сменить на карточке.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { raw ->
                        val letters = raw.filter { it.isLetter() }.take(MAX_SYMBOL_LETTERS)
                        draft = canonicalSpelling(letters) ?: letters
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Например, А или СТ") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Text("Первый сигнал", style = MaterialTheme.typography.titleMedium)
                SignalPicker(
                    enabled = true,
                    selected = first,
                    idleColor = MaterialTheme.colorScheme.surfaceVariant,
                    onPick = { first = it },
                )
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SignalPicker(
    enabled: Boolean,
    selected: SignalId?,
    idleColor: Color,
    onPick: (SignalId) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SignalId.entries.forEach { signal ->
            val chosen = signal == selected
            Surface(
                onClick = { onPick(signal) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "${signal.title}, ${signal.patternName}" },
                shape = MaterialTheme.shapes.medium,
                color = if (chosen) MaterialTheme.colorScheme.primary else idleColor,
                contentColor = if (chosen) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Text(
                    signal.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WideButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
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
private fun SmallButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

private fun signalsWord(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "сигналов"
        mod10 == 1 -> "сигнал"
        mod10 in 2..4 -> "сигнала"
        else -> "сигналов"
    }
}
