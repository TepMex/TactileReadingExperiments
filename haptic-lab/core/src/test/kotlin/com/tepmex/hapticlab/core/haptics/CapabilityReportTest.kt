package com.tepmex.hapticlab.core.haptics

import com.tepmex.hapticlab.core.signal.amplitudeContrastProbe
import com.tepmex.hapticlab.core.signal.timedPulseProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportTest {

    @Test
    fun reportListsTheSixReaderChecksInOrder() {
        val report = capabilityReport(supportedFacts(sdk = 36))
        assertEquals(
            listOf(
                CapabilityIds.TIMED_PULSES,
                CapabilityIds.AMPLITUDE,
                CapabilityIds.EFFECTS,
                CapabilityIds.PRIMITIVES,
                CapabilityIds.DURATIONS,
                CapabilityIds.ENVELOPES,
            ),
            report.map { it.id },
        )
        assertEquals("createOneShot(), createWaveform()", report[0].method)
        assertEquals("Основа ритмического кодирования", report[0].readerValue)
        assertEquals("hasAmplitudeControl()", report[1].method)
        assertEquals(
            "Если false, ненулевые амплитуды waveform превращаются в одинаковое «включено»",
            report[1].readerValue,
        )
        assertEquals("areEffectsSupported()", report[2].method)
        assertEquals("Могут дать дополнительные ощущения", report[2].readerValue)
        assertEquals(PREDEFINED_EFFECT_NAMES, report[2].items.map { it.name })
        assertEquals("arePrimitivesSupported()", report[3].method)
        assertEquals(
            "Позволяют собирать последовательности из аппаратно реализованных эффектов",
            report[3].readerValue,
        )
        assertEquals(
            listOf("TICK", "LOW_TICK", "CLICK", "THUD", "SPIN", "QUICK_RISE", "SLOW_RISE", "QUICK_FALL"),
            report[3].items.map { it.name },
        )
        assertEquals("getPrimitiveDurations()", report[4].method)
        assertEquals("Помогает рассчитывать последовательности", report[4].readerValue)
        assertEquals("areEnvelopeEffectsSupported()", report[5].method)
        assertEquals("Расширенное управление, только при поддержке устройством", report[5].readerValue)
        assertTrue(report.all { it.outcome == ProbeOutcome.SUPPORTED })
    }

    @Test
    fun missingAmplitudeCollapsesNonZeroLevels() {
        val amplitude = capabilityReport(supportedFacts(amplitude = false)).first { it.id == CapabilityIds.AMPLITUDE }
        assertEquals(ProbeOutcome.UNSUPPORTED, amplitude.outcome)
        assertEquals("Не поддерживается", amplitude.outcome.titleRu())
        assertTrue(amplitude.summary.contains("одинаковое «включено»"))
    }

    @Test
    fun api29CannotQueryEffectsPrimitivesDurationsOrEnvelopes() {
        val report = capabilityReport(supportedFacts(sdk = 29)).associateBy { it.id }
        assertEquals(ProbeOutcome.SUPPORTED, report.getValue(CapabilityIds.TIMED_PULSES).outcome)
        assertEquals(ProbeOutcome.SUPPORTED, report.getValue(CapabilityIds.AMPLITUDE).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.EFFECTS).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.PRIMITIVES).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.DURATIONS).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.ENVELOPES).outcome)
        assertTrue(report.getValue(CapabilityIds.EFFECTS).items.isEmpty())
        assertTrue(report.getValue(CapabilityIds.DURATIONS).summary.contains("API 31"))
        assertTrue(report.getValue(CapabilityIds.ENVELOPES).summary.contains("API 36"))
    }

    @Test
    fun api30QueriesEffectsAndPrimitivesButNotDurations() {
        val report = capabilityReport(supportedFacts(sdk = 30)).associateBy { it.id }
        assertEquals(ProbeOutcome.SUPPORTED, report.getValue(CapabilityIds.EFFECTS).outcome)
        assertEquals(ProbeOutcome.SUPPORTED, report.getValue(CapabilityIds.PRIMITIVES).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.DURATIONS).outcome)
        assertEquals(ProbeOutcome.API_UNAVAILABLE, report.getValue(CapabilityIds.ENVELOPES).outcome)
        assertTrue(report.getValue(CapabilityIds.DURATIONS).items.isEmpty())
    }

    @Test
    fun api31ShowsDeclaredDurationsWithoutCallingThemMeasured() {
        val durations = capabilityReport(
            supportedFacts(
                sdk = 31,
                primitives = listOf(
                    primitive("CLICK", supported = true, duration = 12),
                    primitive("TICK", supported = true, duration = 8),
                    primitive("SPIN", supported = false, duration = 0),
                ),
            ),
        ).first { it.id == CapabilityIds.DURATIONS }
        assertEquals(ProbeOutcome.PARTIAL, durations.outcome)
        assertEquals("8 мс", durations.items.first { it.name == "TICK" }.reading)
        assertEquals("0 мс", durations.items.first { it.name == "SPIN" }.reading)
        assertEquals(ProbeOutcome.UNSUPPORTED, durations.items.first { it.name == "SPIN" }.outcome)
        assertTrue(durations.summary.contains("не измеренное время остановки мотора"))
        assertEquals(
            ProbeOutcome.API_UNAVAILABLE,
            capabilityReport(supportedFacts(sdk = 31)).first { it.id == CapabilityIds.ENVELOPES }.outcome,
        )
    }

    @Test
    fun envelopeFlagIsFalseOnAndroid16WithoutHardwareSupport() {
        val envelopes = capabilityReport(supportedFacts(sdk = 36, envelope = false))
            .first { it.id == CapabilityIds.ENVELOPES }
        assertEquals(ProbeOutcome.UNSUPPORTED, envelopes.outcome)
        assertTrue(envelopes.summary.contains("не поддерживает огибающие"))
    }

    @Test
    fun partialEffectSupportNamesWhatWorks() {
        val effects = capabilityReport(
            supportedFacts(
                effects = listOf(
                    EffectReading("TICK", ProbeOutcome.SUPPORTED, "да"),
                    EffectReading("CLICK", ProbeOutcome.SUPPORTED, "да"),
                    EffectReading("HEAVY_CLICK", ProbeOutcome.UNSUPPORTED, "нет, возможен общий запасной шаблон"),
                    EffectReading("DOUBLE_CLICK", ProbeOutcome.UNKNOWN, "неизвестно"),
                ),
            ),
        ).first { it.id == CapabilityIds.EFFECTS }
        assertEquals(ProbeOutcome.PARTIAL, effects.outcome)
        assertEquals("Частично", effects.outcome.titleRu())
        assertTrue(effects.summary.contains("Да: TICK, CLICK"))
        assertTrue(effects.summary.contains("Нет: HEAVY_CLICK"))
        assertTrue(effects.summary.contains("Неизвестно: DOUBLE_CLICK"))
        assertEquals("нет, возможен общий запасной шаблон", effects.items.first { it.name == "HEAVY_CLICK" }.reading)
    }

    @Test
    fun noVibratorBlocksEveryHardwareQuery() {
        val report = capabilityReport(supportedFacts(vibrator = false))
        assertTrue(report.all { it.outcome == ProbeOutcome.NO_VIBRATOR })
        assertTrue(report.first { it.id == CapabilityIds.EFFECTS }.items.isEmpty())
    }

    @Test
    fun probesUseAPauseAndTwoDifferentAmplitudes() {
        val timed = timedPulseProbe()
        assertEquals(listOf(40L, 40L, 40L, 40L, 40L), timed.timingsMs)
        assertEquals(listOf(200, 0, 200, 0, 200), timed.amplitudes)
        val contrast = amplitudeContrastProbe()
        assertEquals(listOf(80L, 160L, 80L), contrast.timingsMs)
        assertEquals(listOf(48, 0, 220), contrast.amplitudes)
        assertTrue(contrast.amplitudes.filter { it > 0 }.distinct().size == 2)
    }

    private fun supportedFacts(
        sdk: Int = 36,
        vibrator: Boolean = true,
        amplitude: Boolean = true,
        envelope: Boolean? = true,
        effects: List<EffectReading>? = null,
        primitives: List<PrimitiveReading>? = null,
    ): CapabilityFacts {
        val durationsAvailable = sdk >= API_PRIMITIVE_DURATION
        val defaultPrimitives = listOf(
            primitive("TICK", supported = true, duration = if (durationsAvailable) 8 else null),
            primitive("LOW_TICK", supported = true, duration = if (durationsAvailable) 10 else null),
            primitive("CLICK", supported = true, duration = if (durationsAvailable) 12 else null),
            primitive("THUD", supported = true, duration = if (durationsAvailable) 20 else null),
            primitive("SPIN", supported = true, duration = if (durationsAvailable) 18 else null),
            primitive("QUICK_RISE", supported = true, duration = if (durationsAvailable) 15 else null),
            primitive("SLOW_RISE", supported = true, duration = if (durationsAvailable) 30 else null),
            primitive("QUICK_FALL", supported = true, duration = if (durationsAvailable) 15 else null),
        )
        return CapabilityFacts(
            sdkInt = sdk,
            hasVibrator = vibrator,
            hasAmplitudeControl = amplitude && vibrator,
            effects = effects ?: PREDEFINED_EFFECT_NAMES.map { EffectReading(it, ProbeOutcome.SUPPORTED, "да") },
            primitives = primitives ?: defaultPrimitives,
            envelopeSupported = if (sdk >= API_ENVELOPE_EFFECTS) envelope else null,
        )
    }

    private fun primitive(name: String, supported: Boolean, duration: Int?) = PrimitiveReading(
        name = name,
        outcome = if (supported) ProbeOutcome.SUPPORTED else ProbeOutcome.UNSUPPORTED,
        reading = if (supported) "да" else "нет",
        durationMs = duration,
    )
}
