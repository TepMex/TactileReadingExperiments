package com.tepmex.vibealphabet.core

/**
 * Один промежуток команды: вибрация на полной силе или тишина.
 * Короче 1 мс участок не бывает — так устроена команда мотору.
 */
data class Segment(
    val on: Boolean,
    val durationMs: Int,
) {
    init {
        require(durationMs > 0) { "Участок должен быть длиннее 0 мс" }
    }
}

/**
 * Рисунок сигнала внутри одного окна.
 * Соседние участки разной полярности: два включённых подряд — это один участок.
 * Сумма длительностей равна окну.
 */
data class Pattern(
    val segments: List<Segment>,
) {
    init {
        require(segments.isNotEmpty()) { "В сигнале нет участков" }
        require(segments.zipWithNext().all { (left, right) -> left.on != right.on }) {
            "Соседние участки одной полярности нужно слить"
        }
    }

    val totalMs: Int get() = segments.sumOf { it.durationMs }

    fun toWaveform(): Waveform = Waveform(
        timingsMs = segments.map { it.durationMs.toLong() },
        amplitudes = segments.map { segment -> if (segment.on) ON_AMPLITUDE else 0 },
    )
}

/** Самая длинная длительность одного участка, если каждый другой оставляет себе 1 мс. */
fun maxSegmentDuration(pattern: Pattern): Int =
    pattern.totalMs - pattern.segments.size + 1

fun canAddSegment(pattern: Pattern): Boolean = pattern.totalMs > pattern.segments.size

fun defaultPattern(signal: SignalId, windowMs: Int): Pattern {
    val waveform = waveformFor(signal, windowMs)
    return Pattern(
        waveform.timingsMs.zip(waveform.amplitudes).map { (duration, amplitude) ->
            Segment(on = amplitude > 0, durationMs = duration.toInt())
        },
    )
}

/** Сливает соседние участки одной полярности. Пустой список недопустим. */
fun coalesce(segments: List<Segment>): List<Segment> {
    val merged = mutableListOf<Segment>()
    for (segment in segments) {
        val last = merged.lastOrNull()
        if (last != null && last.on == segment.on) {
            val duration = last.durationMs.toLong() + segment.durationMs
            require(duration <= MAX_SEGMENT_MS) { "Участок длиннее $MAX_SEGMENT_MS мс" }
            merged[merged.lastIndex] = last.copy(durationMs = duration.toInt())
        } else {
            merged += segment
        }
    }
    require(merged.isNotEmpty()) { "В сигнале нет участков" }
    return merged
}

/**
 * Меняет длительность участка. Сумма остаётся прежней:
 * лишнее забирается у следующих участков, затем у предыдущих;
 * освободившееся время получает ближайший следующий, а у последнего — предыдущий.
 * Каждый другой участок остаётся не короче 1 мс.
 */
fun setSegmentDuration(pattern: Pattern, index: Int, requestedMs: Int): Pattern {
    val segments = pattern.segments
    require(index in segments.indices) { "Нет участка ${index + 1}" }
    if (segments.size == 1) return pattern
    val target = requestedMs.coerceIn(1, maxSegmentDuration(pattern))
    val durations = segments.map { it.durationMs }.toMutableList()
    val delta = target - durations[index]
    if (delta == 0) return pattern
    durations[index] = target
    val partners = neighborOrder(index, segments.lastIndex)
    if (delta > 0) {
        var remaining = delta
        for (partner in partners) {
            if (remaining == 0) break
            val spare = durations[partner] - 1
            val take = minOf(spare, remaining)
            durations[partner] -= take
            remaining -= take
        }
        check(remaining == 0) { "Не удалось забрать $delta мс у соседних участков" }
    } else {
        durations[partners.first()] += -delta
    }
    return pattern.withDurations(durations)
}

fun toggleSegment(pattern: Pattern, index: Int): Pattern {
    require(index in pattern.segments.indices) { "Нет участка ${index + 1}" }
    val updated = pattern.segments.mapIndexed { segmentIndex, segment ->
        if (segmentIndex == index) segment.copy(on = !segment.on) else segment
    }
    return Pattern(coalesce(updated))
}

/**
 * Добавляет в конец участок противоположной полярности.
 * Время берётся пополам у последнего участка, а если он уже 1 мс — у самого длинного.
 */
fun addSegment(pattern: Pattern): Pattern {
    if (!canAddSegment(pattern)) return pattern
    val segments = pattern.segments.toMutableList()
    val donor = if (segments.last().durationMs >= 2) {
        segments.lastIndex
    } else {
        segments.indices.maxWith(compareBy<Int> { segments[it].durationMs }.thenBy { it })
    }
    val source = segments[donor]
    val take = source.durationMs / 2
    if (take < 1) return pattern
    segments[donor] = source.copy(durationMs = source.durationMs - take)
    segments += Segment(on = !segments.last().on, durationMs = take)
    return Pattern(segments)
}

/**
 * Вставляет в середину участка противоположный промежуток.
 * Нужно хотя бы 3 мс, чтобы слева, в середине и справа осталось по 1 мс.
 */
fun splitSegment(pattern: Pattern, index: Int): Pattern {
    val segments = pattern.segments
    require(index in segments.indices) { "Нет участка ${index + 1}" }
    val segment = segments[index]
    if (segment.durationMs < 3) return pattern
    val middle = segment.durationMs / 3
    val left = (segment.durationMs - middle) / 2
    val right = segment.durationMs - middle - left
    val updated = segments.toMutableList()
    updated[index] = segment.copy(durationMs = left)
    updated.add(index + 1, Segment(on = !segment.on, durationMs = middle))
    updated.add(index + 2, Segment(on = segment.on, durationMs = right))
    return Pattern(updated)
}

/** Отдаёт время убранного участка предыдущему, а если убран первый — следующему. */
fun removeSegment(pattern: Pattern, index: Int): Pattern {
    val segments = pattern.segments
    if (segments.size <= 1) return pattern
    require(index in segments.indices) { "Нет участка ${index + 1}" }
    val removed = segments[index]
    val rest = segments.filterIndexed { segmentIndex, _ -> segmentIndex != index }.toMutableList()
    val receiver = if (index == 0) 0 else index - 1
    val host = rest[receiver]
    rest[receiver] = host.copy(durationMs = host.durationMs + removed.durationMs)
    return Pattern(coalesce(rest))
}

/**
 * Масштабирует рисунок в другое окно. Сумма снова равна окну, каждый участок не короче 1 мс.
 * Если участков больше, чем миллисекунд в новом окне, самые короткие сливаются с соседом.
 */
fun scalePattern(pattern: Pattern, targetWindowMs: Int): Pattern {
    require(targetWindowMs >= 1) { "Окно должно быть длиннее 0 мс" }
    val fitted = shrinkToSegmentBudget(pattern, targetWindowMs)
    val durations = scaleDurations(fitted.segments.map { it.durationMs }, targetWindowMs)
    return fitted.withDurations(durations)
}

/** «+20,-80» — вибрация 20 мс, затем тишина 80 мс. */
fun encodePattern(pattern: Pattern): String =
    pattern.segments.joinToString(",") { segment ->
        val sign = if (segment.on) "+" else "-"
        "$sign${segment.durationMs}"
    }

fun decodePattern(raw: String): Pattern? {
    if (raw.isBlank()) return null
    val segments = raw.split(',').map { token ->
        val trimmed = token.trim()
        val sign = trimmed.firstOrNull() ?: return null
        if (sign != '+' && sign != '-') return null
        val body = trimmed.drop(1)
        if (body.isEmpty() || body.any { !it.isDigit() }) return null
        val duration = body.toIntOrNull() ?: return null
        if (duration !in 1..MAX_SEGMENT_MS) return null
        Segment(on = sign == '+', durationMs = duration)
    }
    if (segments.sumOf { it.durationMs.toLong() } > MAX_SEGMENT_MS) return null
    return runCatching { Pattern(coalesce(segments)) }.getOrNull()
}

/**
 * Окно и замены шести рисунков. Эталон в замены не пишется:
 * пока человек его не менял, сигнал следует встроенной таблице.
 */
data class PatternBook(
    val windowMs: Int,
    val overrides: Map<SignalId, Pattern> = emptyMap(),
) {
    init {
        require(windowMs in allowedWindowMillis) { "Окно $windowMs мс недопустимо" }
        for ((signal, pattern) in overrides) {
            require(pattern.totalMs == windowMs) {
                "Рисунок «${signal.patternName}» занимает ${pattern.totalMs} мс при окне $windowMs мс"
            }
            require(pattern != defaultPattern(signal, windowMs)) {
                "Эталон «${signal.patternName}» не хранится как замена"
            }
        }
    }

    fun pattern(signal: SignalId): Pattern = overrides[signal] ?: defaultPattern(signal, windowMs)

    fun isEdited(signal: SignalId): Boolean = signal in overrides

    fun withWindow(windowMs: Int): PatternBook {
        require(windowMs in allowedWindowMillis) { "Окно $windowMs мс недопустимо" }
        if (windowMs == this.windowMs) return this
        val scaled = overrides.mapNotNull { (signal, pattern) ->
            val next = scalePattern(pattern, windowMs)
            if (next == defaultPattern(signal, windowMs)) null else signal to next
        }.toMap()
        return PatternBook(windowMs, scaled)
    }

    fun withPattern(signal: SignalId, pattern: Pattern): PatternBook {
        require(pattern.totalMs == windowMs) {
            "Рисунок занимает ${pattern.totalMs} мс при окне $windowMs мс"
        }
        val next = if (pattern == defaultPattern(signal, windowMs)) {
            overrides - signal
        } else {
            overrides + (signal to pattern)
        }
        return copy(overrides = next)
    }
}

fun patternBookFromStored(windowMs: Int, encodedOverrides: Map<SignalId, String>): PatternBook {
    val window = normalizeWindow(windowMs)
    val overrides = encodedOverrides.mapNotNull { (signal, raw) ->
        val decoded = decodePattern(raw) ?: return@mapNotNull null
        val fitted = if (decoded.totalMs == window) decoded else scalePattern(decoded, window)
        if (fitted == defaultPattern(signal, window)) null else signal to fitted
    }.toMap()
    return PatternBook(window, overrides)
}

private const val MAX_SEGMENT_MS = 60_000

private fun Pattern.withDurations(durations: List<Int>): Pattern {
    require(durations.size == segments.size) { "Число длительностей не совпало с участками" }
    return Pattern(
        segments.mapIndexed { index, segment -> segment.copy(durationMs = durations[index]) },
    )
}

private fun neighborOrder(index: Int, last: Int): List<Int> {
    val after = ((index + 1)..last).toList()
    val before = ((index - 1) downTo 0).toList()
    return after + before
}

private fun shrinkToSegmentBudget(pattern: Pattern, budget: Int): Pattern {
    var current = pattern
    while (current.segments.size > budget) {
        val index = current.segments.indices.minWith(
            compareBy<Int> { current.segments[it].durationMs }.thenByDescending { it },
        )
        val next = removeSegment(current, index)
        check(next.segments.size < current.segments.size) { "Участок не слился" }
        current = next
    }
    return current
}

/**
 * Целочисленное масштабирование по наибольшему остатку.
 * Сначала каждый участок получает целую долю, но не меньше 1 мс, затем разница
 * добирается или снимается так, чтобы сумма стала ровно [target].
 */
private fun scaleDurations(durations: List<Int>, target: Int): List<Int> {
    val count = durations.size
    require(count >= 1) { "В сигнале нет участков" }
    require(target >= count) { "Окно $target мс короче числа участков $count" }
    val source = durations.sum()
    require(source >= count) { "Участок должен быть длиннее 0 мс" }
    if (source == target) return durations
    val floors = IntArray(count) { index ->
        (durations[index].toLong() * target / source).toInt()
    }
    val remainders = IntArray(count) { index ->
        (durations[index].toLong() * target % source).toInt()
    }
    val scaled = floors.copyOf()
    for (index in scaled.indices) {
        if (scaled[index] < 1) scaled[index] = 1
    }
    var sum = scaled.sum()
    if (sum < target) {
        var extra = target - sum
        val order = scaled.indices.sortedWith(
            compareByDescending<Int> { remainders[it] }.thenBy { it },
        )
        var cursor = 0
        while (extra > 0) {
            scaled[order[cursor % count]]++
            extra--
            cursor++
        }
    } else if (sum > target) {
        var extra = sum - target
        val order = scaled.indices.sortedWith(
            compareByDescending<Int> { scaled[it] - floors[it] }.thenBy { it },
        )
        for (index in order) {
            if (extra == 0) break
            val spare = scaled[index] - 1
            if (spare <= 0) continue
            val take = minOf(spare, extra)
            scaled[index] -= take
            extra -= take
        }
        check(extra == 0) { "Не удалось уложить участки в $target мс" }
    }
    check(scaled.sum() == target) { "Сумма ${scaled.sum()} мс не равна окну $target мс" }
    check(scaled.all { it >= 1 }) { "Участок должен быть длиннее 0 мс" }
    return scaled.toList()
}
