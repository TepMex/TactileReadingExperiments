package com.tepmex.hapticlab.core.haptics

/**
 * Шесть проверок вибромотора, от которых зависит ритмическое кодирование читалки.
 * Отчёт строится из уже снятых ответов платформы и не вызывает Android API.
 */
enum class ProbeOutcome {
    SUPPORTED,
    UNSUPPORTED,
    PARTIAL,
    UNKNOWN,
    API_UNAVAILABLE,
    NO_VIBRATOR,
}

fun ProbeOutcome.titleRu(): String = when (this) {
    ProbeOutcome.SUPPORTED -> "Поддерживается"
    ProbeOutcome.UNSUPPORTED -> "Не поддерживается"
    ProbeOutcome.PARTIAL -> "Частично"
    ProbeOutcome.UNKNOWN -> "Неизвестно"
    ProbeOutcome.API_UNAVAILABLE -> "API недоступен"
    ProbeOutcome.NO_VIBRATOR -> "Нет вибромотора"
}

data class ProbeItem(
    val name: String,
    val outcome: ProbeOutcome,
    val reading: String,
)

data class CapabilityProbe(
    val id: String,
    val title: String,
    val method: String,
    val readerValue: String,
    val outcome: ProbeOutcome,
    val summary: String,
    val items: List<ProbeItem> = emptyList(),
)

data class EffectReading(
    val name: String,
    val outcome: ProbeOutcome,
    val reading: String,
)

data class PrimitiveReading(
    val name: String,
    val outcome: ProbeOutcome,
    val reading: String,
    /** Null, пока нет getPrimitiveDurations() (API 31). */
    val durationMs: Int? = null,
)

data class CapabilityFacts(
    val sdkInt: Int,
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val effects: List<EffectReading>,
    val primitives: List<PrimitiveReading>,
    /** Null, пока нет areEnvelopeEffectsSupported() (API 36). */
    val envelopeSupported: Boolean?,
)

object CapabilityIds {
    const val TIMED_PULSES = "timed-pulses"
    const val AMPLITUDE = "amplitude"
    const val EFFECTS = "effects"
    const val PRIMITIVES = "primitives"
    const val DURATIONS = "primitive-durations"
    const val ENVELOPES = "envelopes"
}

const val API_EFFECT_QUERY = 30
const val API_PRIMITIVE_DURATION = 31
const val API_ENVELOPE_EFFECTS = 36

val PREDEFINED_EFFECT_NAMES = listOf("TICK", "CLICK", "HEAVY_CLICK", "DOUBLE_CLICK")

private val PRIMITIVE_ORDER = listOf(
    "TICK",
    "LOW_TICK",
    "CLICK",
    "THUD",
    "SPIN",
    "QUICK_RISE",
    "SLOW_RISE",
    "QUICK_FALL",
)

fun capabilityReport(facts: CapabilityFacts): List<CapabilityProbe> = listOf(
    timedPulses(facts),
    amplitude(facts),
    predefinedEffects(facts),
    primitives(facts),
    primitiveDurations(facts),
    envelopes(facts),
)

private fun timedPulses(facts: CapabilityFacts): CapabilityProbe {
    val outcome = if (facts.hasVibrator) ProbeOutcome.SUPPORTED else ProbeOutcome.NO_VIBRATOR
    val summary = if (facts.hasVibrator) {
        "Вибромотор есть. Длительность импульса и пауза задаются одним вызовом createOneShot или createWaveform."
    } else {
        "Вибромотор не найден, импульс заданной длины и паузы послать некуда."
    }
    return CapabilityProbe(
        id = CapabilityIds.TIMED_PULSES,
        title = "Импульсы заданной длительности и паузы",
        method = "createOneShot(), createWaveform()",
        readerValue = "Основа ритмического кодирования",
        outcome = outcome,
        summary = summary,
    )
}

private fun amplitude(facts: CapabilityFacts): CapabilityProbe {
    val (outcome, summary) = when {
        !facts.hasVibrator -> ProbeOutcome.NO_VIBRATOR to
            "Вибромотор не найден, амплитуду проверять не на чем."
        facts.hasAmplitudeControl -> ProbeOutcome.SUPPORTED to
            "Разные ненулевые амплитуды waveform остаются разными уровнями."
        else -> ProbeOutcome.UNSUPPORTED to
            "Ненулевые амплитуды waveform превращаются в одинаковое «включено»."
    }
    return CapabilityProbe(
        id = CapabilityIds.AMPLITUDE,
        title = "Управление амплитудой",
        method = "hasAmplitudeControl()",
        readerValue = "Если false, ненулевые амплитуды waveform превращаются в одинаковое «включено»",
        outcome = outcome,
        summary = summary,
    )
}

private fun predefinedEffects(facts: CapabilityFacts): CapabilityProbe {
    val gated = gate(
        facts = facts,
        minimumSdk = API_EFFECT_QUERY,
        unavailable = "areEffectsSupported() есть с API 30. На SDK ${facts.sdkInt} готовые эффекты так не опрашиваются.",
    )
    val items = if (gated != null) {
        emptyList()
    } else {
        namedItems(facts.effects, PREDEFINED_EFFECT_NAMES)
    }
    return CapabilityProbe(
        id = CapabilityIds.EFFECTS,
        title = "Готовые эффекты TICK, CLICK, HEAVY_CLICK, DOUBLE_CLICK",
        method = "areEffectsSupported()",
        readerValue = "Могут дать дополнительные ощущения",
        outcome = gated?.first ?: rollup(items),
        summary = gated?.second ?: groupSummary(items, "эффект"),
        items = items,
    )
}

private fun primitives(facts: CapabilityFacts): CapabilityProbe {
    val gated = gate(
        facts = facts,
        minimumSdk = API_EFFECT_QUERY,
        unavailable = "arePrimitivesSupported() есть с API 30. На SDK ${facts.sdkInt} примитивы так не опрашиваются.",
    )
    val items = if (gated != null) emptyList() else orderedPrimitiveItems(facts.primitives) { it.reading to it.outcome }
    return CapabilityProbe(
        id = CapabilityIds.PRIMITIVES,
        title = "Примитивы TICK, LOW_TICK, CLICK, THUD и другие",
        method = "arePrimitivesSupported()",
        readerValue = "Позволяют собирать последовательности из аппаратно реализованных эффектов",
        outcome = gated?.first ?: rollup(items),
        summary = gated?.second ?: groupSummary(items, "примитив"),
        items = items,
    )
}

private fun primitiveDurations(facts: CapabilityFacts): CapabilityProbe {
    val gated = gate(
        facts = facts,
        minimumSdk = API_PRIMITIVE_DURATION,
        unavailable = "getPrimitiveDurations() есть с API 31. На SDK ${facts.sdkInt} заявленной длительности нет.",
    )
    val items = if (gated != null) {
        emptyList()
    } else {
        orderedPrimitiveItems(facts.primitives) { primitive ->
            val ms = primitive.durationMs
            when {
                ms == null -> "нет данных" to ProbeOutcome.UNKNOWN
                primitive.outcome == ProbeOutcome.SUPPORTED && ms > 0 -> "$ms мс" to ProbeOutcome.SUPPORTED
                else -> "$ms мс" to ProbeOutcome.UNSUPPORTED
            }
        }
    }
    val summary = gated?.second ?: (
        "Заявленные миллисекунды. Ноль не помогает рассчитать последовательность. " +
            "Это не измеренное время остановки мотора."
        )
    return CapabilityProbe(
        id = CapabilityIds.DURATIONS,
        title = "Заявленная длительность примитивов",
        method = "getPrimitiveDurations()",
        readerValue = "Помогает рассчитывать последовательности",
        outcome = gated?.first ?: rollup(items),
        summary = summary,
        items = items,
    )
}

private fun envelopes(facts: CapabilityFacts): CapabilityProbe {
    val gated = gate(
        facts = facts,
        minimumSdk = API_ENVELOPE_EFFECTS,
        unavailable = "areEnvelopeEffectsSupported() есть с Android 16 (API 36). На SDK ${facts.sdkInt} огибающие недоступны.",
    )
    val (outcome, summary) = gated ?: when (facts.envelopeSupported) {
        true -> ProbeOutcome.SUPPORTED to
            "Можно менять интенсивность и частоту. BasicEnvelopeBuilder — интенсивность, WaveformEnvelopeBuilder — частота."
        false -> ProbeOutcome.UNSUPPORTED to
            "Устройство не поддерживает огибающие. Расширенное управление интенсивностью и частотой недоступно."
        null -> ProbeOutcome.UNKNOWN to "Опрос areEnvelopeEffectsSupported() не дал ответа."
    }
    return CapabilityProbe(
        id = CapabilityIds.ENVELOPES,
        title = "Огибающие с изменением интенсивности и частоты, Android 16+",
        method = "areEnvelopeEffectsSupported()",
        readerValue = "Расширенное управление, только при поддержке устройством",
        outcome = outcome,
        summary = summary,
    )
}

private fun gate(
    facts: CapabilityFacts,
    minimumSdk: Int,
    unavailable: String,
): Pair<ProbeOutcome, String>? {
    if (facts.sdkInt < minimumSdk) return ProbeOutcome.API_UNAVAILABLE to unavailable
    if (!facts.hasVibrator) {
        return ProbeOutcome.NO_VIBRATOR to "Вибромотор не найден, опрос оборудования не к чему применить."
    }
    return null
}

private fun namedItems(readings: List<EffectReading>, order: List<String>): List<ProbeItem> {
    val byName = readings.associateBy { it.name }
    return order.map { name ->
        val found = byName[name]
        if (found == null) {
            ProbeItem(name, ProbeOutcome.UNKNOWN, "нет данных")
        } else {
            ProbeItem(name, found.outcome, found.reading)
        }
    }
}

private fun orderedPrimitiveItems(
    readings: List<PrimitiveReading>,
    render: (PrimitiveReading) -> Pair<String, ProbeOutcome>,
): List<ProbeItem> {
    val byName = readings.associateBy { it.name }
    val names = PRIMITIVE_ORDER.filter { it in byName } + readings.map { it.name }.filter { it !in PRIMITIVE_ORDER }
    return names.map { name ->
        val primitive = byName.getValue(name)
        val (reading, outcome) = render(primitive)
        ProbeItem(name, outcome, reading)
    }
}

private fun rollup(items: List<ProbeItem>): ProbeOutcome {
    if (items.isEmpty()) return ProbeOutcome.UNKNOWN
    val outcomes = items.map { it.outcome }
    return when {
        outcomes.all { it == ProbeOutcome.SUPPORTED } -> ProbeOutcome.SUPPORTED
        outcomes.all { it == ProbeOutcome.UNSUPPORTED } -> ProbeOutcome.UNSUPPORTED
        outcomes.all { it == ProbeOutcome.UNKNOWN } -> ProbeOutcome.UNKNOWN
        outcomes.all { it == ProbeOutcome.API_UNAVAILABLE } -> ProbeOutcome.API_UNAVAILABLE
        outcomes.any { it == ProbeOutcome.SUPPORTED } -> ProbeOutcome.PARTIAL
        outcomes.any { it == ProbeOutcome.UNKNOWN } -> ProbeOutcome.UNKNOWN
        else -> ProbeOutcome.PARTIAL
    }
}

private fun groupSummary(items: List<ProbeItem>, kind: String): String {
    val yes = items.filter { it.outcome == ProbeOutcome.SUPPORTED }.map { it.name }
    val no = items.filter { it.outcome == ProbeOutcome.UNSUPPORTED }.map { it.name }
    val unknown = items.filter { it.outcome == ProbeOutcome.UNKNOWN }.map { it.name }
    return when {
        items.isEmpty() -> "Опрос не вернул ни одного значения."
        yes.size == items.size -> "Все опрошенные ${kind}ы поддерживаются."
        no.size == items.size -> "Ни один опрошенный $kind не поддерживается."
        else -> buildString {
            if (yes.isNotEmpty()) append("Да: ${yes.joinToString(", ")}. ")
            if (no.isNotEmpty()) append("Нет: ${no.joinToString(", ")}. ")
            if (unknown.isNotEmpty()) append("Неизвестно: ${unknown.joinToString(", ")}.")
        }.trim()
    }
}
