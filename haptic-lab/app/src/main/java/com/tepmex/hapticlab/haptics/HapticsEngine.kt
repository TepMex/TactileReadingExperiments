package com.hapticlab.haptics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hapticlab.core.haptics.freeDecayTo10PercentMillis
import com.hapticlab.core.log.DeviceSnapshot
import com.hapticlab.core.signal.WaveformSpec
import com.hapticlab.core.signal.buildDiscriminationWaveform

private const val API_MANAGER = 31
private const val API_ATTRIBUTES = 33
private const val API_Q_FACTOR = 34
private const val API_ENVELOPE = 36
private const val API_BUILDER = 3_700_002

data class DiagnosticRow(val title: String, val value: String, val detail: String? = null)

data class NamedEffect(val name: String, val id: Int, val support: String)

data class HapticInventory(
    val rows: List<DiagnosticRow>,
    val snapshot: DeviceSnapshot,
    val effects: List<NamedEffect>,
    val primitives: List<NamedEffect>,
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val envelopeSupported: Boolean,
    val waveformFrequencyHz: Float?,
    val minEnvelopeStepMs: Long,
    val maxEnvelopePoints: Int,
    val builderSupported: Boolean,
)

data class PlayResult(
    val accepted: Boolean,
    val message: String,
    val commandedAtElapsedRealtimeNanos: Long,
    val commandedDurationMs: Long,
)

class HapticsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var safetyStop: Runnable? = null

    fun stop() {
        safetyStop?.let(handler::removeCallbacks)
        safetyStop = null
        try {
            vibrator()?.cancel()
            if (Build.VERSION.SDK_INT >= API_MANAGER) {
                appContext.getSystemService(VibratorManager::class.java)?.cancel()
            }
        } catch (error: RuntimeException) {
            // Отдельные прошивки бросают исключение из HAL. Команду остановки всё равно запросили.
        }
    }

    fun inventoryOrFailure(): HapticInventory {
        return try {
            inventory()
        } catch (error: RuntimeException) {
            HapticInventory(
                rows = listOf(
                    DiagnosticRow(
                        "Опрос вибромотора",
                        error.message ?: "исключение платформы",
                        "Дальнейшие команды вибрации не запускаются, пока опрос не завершится без ошибки.",
                    ),
                ),
                snapshot = com.hapticlab.core.log.DeviceSnapshot(
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    model = Build.MODEL.orEmpty(),
                    androidRelease = Build.VERSION.RELEASE.orEmpty(),
                    sdkInt = Build.VERSION.SDK_INT,
                    sdkIntFull = sdkIntFullOrNull(),
                    vibratorCount = 0,
                    hasVibrator = false,
                    hasAmplitudeControl = false,
                    resonantFrequencyHz = null,
                    qFactor = null,
                    freeDecayT10Ms = null,
                    envelopeSupported = null,
                ),
                effects = emptyList(),
                primitives = emptyList(),
                hasVibrator = false,
                hasAmplitudeControl = false,
                envelopeSupported = false,
                waveformFrequencyHz = null,
                minEnvelopeStepMs = 1L,
                maxEnvelopePoints = 16,
                builderSupported = false,
            )
        }
    }

    fun inventory(): HapticInventory {
        val vibrator = vibrator()
        val count = vibratorCount()
        val hasVibrator = vibrator?.hasVibrator() == true
        val amplitude = hasVibrator && vibrator?.hasAmplitudeControl() == true
        val q = readQ(vibrator)
        val frequency = readResonantFrequency(vibrator)
        val t10 = if (q != null && frequency != null) {
            freeDecayTo10PercentMillis(q.toDouble(), frequency.toDouble())
        } else {
            null
        }
        val envelope = readEnvelopeSupported(vibrator)
        val profileNote = readFrequencyProfile(vibrator)
        val envelopeInfo = readEnvelopeInfo(vibrator)
        val full = sdkIntFullOrNull()
        val effects = readEffects(vibrator)
        val primitives = readPrimitives(vibrator)
        val builder = full != null && full >= API_BUILDER && envelope == true
        val rows = buildList {
            add(DiagnosticRow("Производитель", Build.MANUFACTURER.orEmpty()))
            add(DiagnosticRow("Модель", Build.MODEL.orEmpty()))
            add(DiagnosticRow("Версия Android", Build.VERSION.RELEASE.orEmpty()))
            add(DiagnosticRow("SDK_INT", Build.VERSION.SDK_INT.toString()))
            add(DiagnosticRow("SDK_INT_FULL", full?.toString() ?: "поле есть только с API 36"))
            add(DiagnosticRow("Виброактуаторы", count.toString(), "Воспроизведение идёт на актуатор по умолчанию."))
            add(DiagnosticRow("Управление амплитудой", yesNo(amplitude)))
            add(DiagnosticRow("Резонансная частота", hertz(frequency), "Vibrator.getResonantFrequency(), API 34+."))
            add(DiagnosticRow("Добротность Q", numberOrUnavailable(q), "Vibrator.getQFactor(), API 34+."))
            add(
                DiagnosticRow(
                    "t10, оценка свободного затухания",
                    t10?.let { String.format(java.util.Locale.US, "%.2f мс", it) } ?: "Не предоставляется устройством",
                    "t10 = Q·ln(10)/(π·f0). Это не измеренное время остановки и не учитывает активное торможение.",
                ),
            )
            add(DiagnosticRow("Конверты API 36", triState(envelope)))
            add(DiagnosticRow("Профиль частот", profileNote))
            add(DiagnosticRow("Ограничения конверта", envelopeInfo.second))
            add(DiagnosticRow("VibrationEffect.Builder API 37.2", yesNo(builder)))
            if (full != null && full >= API_BUILDER && vibrator != null) {
                add(DiagnosticRow("Пресеты API 37.2", readPresets(vibrator)))
            } else {
                add(DiagnosticRow("Пресеты API 37.2", "Недоступны на этой версии Android"))
            }
            effects.forEach { effect ->
                add(DiagnosticRow("Эффект ${effect.name}", effect.support))
            }
            primitives.forEach { primitive ->
                add(DiagnosticRow("Примитив ${primitive.name}", primitive.support))
            }
            add(
                DiagnosticRow(
                    "Что приложение может знать",
                    "Только уровень А",
                    "Время вызова API — это момент команды. Физический отклик мотора так не измеряется. Встроенный акселерометр не используется: его частоты и фильтры не позволяют считать корпусные данные достоверным измерением LRA.",
                ),
            )
        }
        val snapshot = DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            sdkIntFull = full,
            vibratorCount = count,
            hasVibrator = hasVibrator,
            hasAmplitudeControl = amplitude,
            resonantFrequencyHz = frequency,
            qFactor = q,
            freeDecayT10Ms = t10,
            envelopeSupported = envelope,
            notes = listOf("level_A_only"),
        )
        return HapticInventory(
            rows = rows,
            snapshot = snapshot,
            effects = effects,
            primitives = primitives,
            hasVibrator = hasVibrator,
            hasAmplitudeControl = amplitude,
            envelopeSupported = envelope == true,
            waveformFrequencyHz = readWaveformFrequency(vibrator),
            minEnvelopeStepMs = envelopeInfo.first,
            maxEnvelopePoints = envelopeInfo.third,
            builderSupported = builder,
        )
    }

    fun playOneShot(durationMs: Long, amplitude: Int): PlayResult {
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        val safeDuration = durationMs.coerceIn(1L, 10_000L)
        val effect = VibrationEffect.createOneShot(safeDuration, appliedAmplitude(vibrator, amplitude))
        return dispatch(effect, safeDuration, "createOneShot ${safeDuration} мс")
    }

    fun playWaveform(spec: WaveformSpec): PlayResult {
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        if (spec.timingsMs.isEmpty()) return rejected("Пустой сигнал")
        val timings = spec.timingsMs.toLongArray()
        val amplitudes = IntArray(spec.amplitudes.size) { index ->
            val value = spec.amplitudes[index]
            if (value == 0) 0 else appliedAmplitude(vibrator, value)
        }
        val effect = try {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } catch (error: IllegalArgumentException) {
            return rejected("createWaveform отклонён: ${error.message}")
        }
        val note = buildString {
            append("createWaveform ")
            append(spec.totalMs)
            append(" мс")
            if (spec.truncated) append(", обрезано пределом 15 с")
            if (!vibrator.hasAmplitudeControl()) append(", амплитуда устройства не управляется")
        }
        return dispatch(effect, spec.totalMs, note)
    }

    fun playPredefined(effectId: Int, name: String): PlayResult {
        if (Build.VERSION.SDK_INT < 29) return rejected("createPredefined доступен с API 29")
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        val support = effectSupport(vibrator, effectId)
        val effect = try {
            VibrationEffect.createPredefined(effectId)
        } catch (error: IllegalArgumentException) {
            return rejected("Предопределённый эффект отклонён: ${error.message}")
        }
        return dispatch(effect, 80L, "$name, поддержка: $support. Длительность эффекта устройством не гарантирована.")
    }

    fun playPrimitiveTrain(primitiveId: Int, name: String, pulses: Int, gapMs: Int, amplitude: Int): PlayResult {
        if (Build.VERSION.SDK_INT < 30) return rejected("Композиция примитивов доступна с API 30")
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        val supported = try {
            vibrator.arePrimitivesSupported(primitiveId).firstOrNull() == true
        } catch (error: RuntimeException) {
            return rejected("Проверка примитива не удалась: ${error.message}")
        }
        if (!supported) return rejected("Примитив $name не поддерживается оборудованием")
        val scale = (amplitude.coerceIn(1, 255) / 255f).coerceIn(0.05f, 1f)
        val safePulses = pulses.coerceIn(1, 16)
        val safeGap = gapMs.coerceIn(0, 500)
        var composition = VibrationEffect.startComposition()
        repeat(safePulses) { index ->
            composition = if (index == 0) {
                composition.addPrimitive(primitiveId, scale)
            } else {
                composition.addPrimitive(primitiveId, scale, safeGap)
            }
        }
        val effect = try {
            composition.compose()
        } catch (error: IllegalArgumentException) {
            return rejected("Композиция отклонена: ${error.message}")
        }
        val primitiveMs = primitiveDuration(vibrator, primitiveId)
        val estimated = primitiveMs * safePulses + safeGap * (safePulses - 1)
        return dispatch(
            effect,
            estimated.coerceAtLeast(20),
            "Composition $name × $safePulses, пауза $safeGap мс. Оценка длительности примитива API: $primitiveMs мс, это не измерение.",
        )
    }

    fun playBasicEnvelope(onMs: Long, offMs: Long, pulses: Int, amplitude: Int, inventory: HapticInventory): PlayResult {
        if (Build.VERSION.SDK_INT < API_ENVELOPE) return rejected("BasicEnvelopeBuilder доступен с API 36")
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        if (!vibrator.areEnvelopeEffectsSupported()) return rejected("Оборудование не поддерживает конверты")
        val step = inventory.minEnvelopeStepMs.coerceAtLeast(1L)
        if (onMs < step || (pulses > 1 && offMs < step)) {
            return rejected("Минимальная длительность контрольной точки: $step мс. Значение не подменяется.")
        }
        val points = pulses.coerceIn(1, 12) * 3 + (pulses.coerceIn(1, 12) - 1)
        if (inventory.maxEnvelopePoints in 1 until points) {
            return rejected("Устройству доступно ${inventory.maxEnvelopePoints} контрольных точек, запрошено $points")
        }
        val intensity = (amplitude.coerceIn(1, 255) / 255f).coerceIn(0.05f, 1f)
        val builder = VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(0.7f)
        val count = pulses.coerceIn(1, 12)
        repeat(count) { index ->
            builder.addControlPoint(intensity, 0.7f, step)
            builder.addControlPoint(intensity, 0.7f, onMs)
            builder.addControlPoint(0f, 0.7f, step)
            if (index != count - 1) builder.addControlPoint(0f, 0.5f, offMs)
        }
        val effect = try {
            builder.build()
        } catch (error: IllegalArgumentException) {
            return rejected("Конверт отклонён: ${error.message}")
        }
        val duration = count * (step + onMs + step) + (count - 1) * offMs
        return dispatch(effect, duration, "BasicEnvelopeBuilder. Контрольные точки — запрошенный сигнал, не измеренная вибрация.")
    }

    fun playWaveformEnvelope(onMs: Long, offMs: Long, amplitude: Int, inventory: HapticInventory): PlayResult {
        if (Build.VERSION.SDK_INT < API_ENVELOPE) return rejected("WaveformEnvelopeBuilder доступен с API 36")
        val vibrator = requireVibrator() ?: return rejected("Вибромотор недоступен")
        if (!vibrator.areEnvelopeEffectsSupported()) return rejected("Оборудование не поддерживает конверты")
        val frequency = inventory.waveformFrequencyHz
            ?: return rejected("Профиль частот не предоставлен, частотный конверт не запускается")
        val step = inventory.minEnvelopeStepMs.coerceAtLeast(1L)
        if (onMs < step) return rejected("Минимальная длительность контрольной точки: $step мс")
        val level = (amplitude.coerceIn(1, 255) / 255f).coerceIn(0.05f, 1f)
        val builder = VibrationEffect.WaveformEnvelopeBuilder().setInitialFrequencyHz(frequency)
        builder.addControlPoint(level, frequency, step)
        builder.addControlPoint(level, frequency, onMs)
        builder.addControlPoint(0f, frequency, step)
        val effect = try {
            builder.build()
        } catch (error: IllegalArgumentException) {
            return rejected("Частотный конверт отклонён: ${error.message}")
        }
        return dispatch(effect, step + onMs + step, "WaveformEnvelopeBuilder, $frequency Гц. Без автоматической подмены, если частоты нет в профиле.")
    }

    fun playBuilder(onMs: Long, gapMs: Long, amplitude: Int, inventory: HapticInventory): PlayResult {
        if (sdkIntFullOrNull()?.let { it >= API_BUILDER } != true) {
            return rejected("VibrationEffect.Builder доступен с API 37.2")
        }
        if (!inventory.envelopeSupported) return rejected("Для этого режима нужен поддерживаемый конверт")
        val step = inventory.minEnvelopeStepMs.coerceAtLeast(1L)
        val safeOn = onMs.coerceAtLeast(step)
        if (onMs < step) return rejected("Минимальная длительность контрольной точки: $step мс")
        val intensity = (amplitude.coerceIn(1, 255) / 255f).coerceIn(0.05f, 1f)
        val envelope = VibrationEffect.Envelope.create(
            VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(0.6f)
                .addControlPoint(intensity, 0.6f, safeOn)
                .addControlPoint(0f, 0.6f, step),
        )
        val effect = try {
            VibrationEffect.Builder()
                .addPreset(0L, VibrationEffect.Preset.create(VibrationEffect.Preset.PRESET_CLICK, intensity))
                .addEnvelope(gapMs.coerceAtLeast(0L), envelope)
                .build()
        } catch (error: RuntimeException) {
            return rejected("Builder отклонён: ${error.message}")
        }
        return dispatch(effect, gapMs + safeOn + step, "Builder: пресет и конверт. Время — запрошенная шкала, не отклик мотора.")
    }

    fun playDiscrimination(
        kind: DiscriminationKind,
        onMs: Long,
        gapMs: Long?,
        amplitude: Int,
        inventory: HapticInventory,
    ): PlayResult {
        return when (kind) {
            DiscriminationKind.WAVEFORM -> playWaveform(buildDiscriminationWaveform(onMs, gapMs, amplitude))
            DiscriminationKind.PRIMITIVE -> {
                val primitive = inventory.primitives.firstOrNull { it.name == "CLICK" && it.support == "да" }
                    ?: inventory.primitives.firstOrNull { it.support == "да" }
                if (primitive == null) return rejected("Нет поддерживаемого примитива")
                if (gapMs == null) {
                    playPrimitiveTrain(primitive.id, primitive.name, pulses = 1, gapMs = 0, amplitude = amplitude)
                } else {
                    playPrimitiveTrain(primitive.id, primitive.name, pulses = 2, gapMs = gapMs.toInt(), amplitude = amplitude)
                }
            }
            DiscriminationKind.ENVELOPE -> playBasicEnvelope(
                onMs = onMs,
                offMs = gapMs ?: 0L,
                pulses = if (gapMs == null) 1 else 2,
                amplitude = amplitude,
                inventory = inventory,
            )
        }
    }

    private fun dispatch(effect: VibrationEffect, safetyMs: Long, message: String): PlayResult {
        val vibrator = vibrator() ?: return rejected("Вибромотор недоступен")
        val commandedAt = SystemClock.elapsedRealtimeNanos()
        return try {
            if (Build.VERSION.SDK_INT >= API_ATTRIBUTES) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            } else {
                vibrator.vibrate(effect)
            }
            armSafety(safetyMs)
            PlayResult(true, message, commandedAt, safetyMs)
        } catch (error: RuntimeException) {
            PlayResult(false, "Вызов vibrate отклонён: ${error.message}", commandedAt, 0L)
        }
    }

    private fun armSafety(durationMs: Long) {
        safetyStop?.let(handler::removeCallbacks)
        val task = Runnable { stop() }
        safetyStop = task
        handler.postDelayed(task, durationMs.coerceIn(1L, 60_000L) + 200L)
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= API_MANAGER) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun requireVibrator(): Vibrator? {
        val vibrator = vibrator() ?: return null
        return if (vibrator.hasVibrator()) vibrator else null
    }

    private fun vibratorCount(): Int {
        return if (Build.VERSION.SDK_INT >= API_MANAGER) {
            appContext.getSystemService(VibratorManager::class.java)?.vibratorIds?.size ?: 0
        } else if (vibrator()?.hasVibrator() == true) {
            1
        } else {
            0
        }
    }

    private fun appliedAmplitude(vibrator: Vibrator, amplitude: Int): Int {
        return if (vibrator.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
    }

    private fun readQ(vibrator: Vibrator?): Float? {
        if (vibrator == null || Build.VERSION.SDK_INT < API_Q_FACTOR) return null
        return finiteOrNull(vibrator.qFactor)
    }

    private fun readResonantFrequency(vibrator: Vibrator?): Float? {
        if (vibrator == null || Build.VERSION.SDK_INT < API_Q_FACTOR) return null
        return finiteOrNull(vibrator.resonantFrequency)
    }

    private fun readEnvelopeSupported(vibrator: Vibrator?): Boolean? {
        if (vibrator == null || Build.VERSION.SDK_INT < API_ENVELOPE) return null
        return vibrator.areEnvelopeEffectsSupported()
    }

    private fun readFrequencyProfile(vibrator: Vibrator?): String {
        if (vibrator == null || Build.VERSION.SDK_INT < API_ENVELOPE) return "API 36 недоступен"
        val profile = vibrator.frequencyProfile ?: return "Не предоставляется устройством"
        val min = finiteOrNull(profile.minFrequencyHz)
        val max = finiteOrNull(profile.maxFrequencyHz)
        val accel = finiteOrNull(profile.maxOutputAccelerationGs)
        if (min == null || max == null) return "Не предоставляется устройством"
        val accelText = accel?.let { String.format(java.util.Locale.US, ", до %.3f G", it) }.orEmpty()
        return String.format(java.util.Locale.US, "%.1f–%.1f Гц%s", min, max, accelText)
    }

    private fun readWaveformFrequency(vibrator: Vibrator?): Float? {
        if (vibrator == null || Build.VERSION.SDK_INT < API_ENVELOPE) return null
        val profile = vibrator.frequencyProfile ?: return null
        val min = finiteOrNull(profile.minFrequencyHz) ?: return null
        val max = finiteOrNull(profile.maxFrequencyHz) ?: return null
        val resonant = readResonantFrequency(vibrator)
        return if (resonant != null && resonant in min..max) resonant else min
    }

    private fun readEnvelopeInfo(vibrator: Vibrator?): Triple<Long, String, Int> {
        if (vibrator == null || Build.VERSION.SDK_INT < API_ENVELOPE) {
            return Triple(1L, "API 36 недоступен", 16)
        }
        val info = vibrator.envelopeEffectInfo ?: return Triple(1L, "Не предоставляется устройством", 16)
        val text = "точек до ${info.maxSize}, шаг ${info.minControlPointDurationMillis}–${info.maxControlPointDurationMillis} мс, всего до ${info.maxDurationMillis} мс"
        return Triple(info.minControlPointDurationMillis.coerceAtLeast(1L), text, info.maxSize)
    }

    private fun readEffects(vibrator: Vibrator?): List<NamedEffect> {
        val effects = listOf(
            "CLICK" to VibrationEffect.EFFECT_CLICK,
            "DOUBLE_CLICK" to VibrationEffect.EFFECT_DOUBLE_CLICK,
            "TICK" to VibrationEffect.EFFECT_TICK,
            "HEAVY_CLICK" to VibrationEffect.EFFECT_HEAVY_CLICK,
        )
        if (vibrator == null || Build.VERSION.SDK_INT < 30) {
            return effects.map { NamedEffect(it.first, it.second, "проверка с API 30") }
        }
        return effects.map { (name, id) -> NamedEffect(name, id, effectSupport(vibrator, id)) }
    }

    private fun effectSupport(vibrator: Vibrator, effectId: Int): String {
        if (Build.VERSION.SDK_INT < 30) return "проверка с API 30"
        val code = try {
            vibrator.areEffectsSupported(effectId).firstOrNull()
        } catch (error: RuntimeException) {
            return "ошибка: ${error.message}"
        }
        return when (code) {
            Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> "да"
            Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> "нет, возможен общий запасной шаблон"
            Vibrator.VIBRATION_EFFECT_SUPPORT_UNKNOWN -> "неизвестно"
            else -> "нет данных"
        }
    }

    private fun readPrimitives(vibrator: Vibrator?): List<NamedEffect> {
        val primitives = listOf(
            "CLICK" to VibrationEffect.Composition.PRIMITIVE_CLICK,
            "THUD" to VibrationEffect.Composition.PRIMITIVE_THUD,
            "SPIN" to VibrationEffect.Composition.PRIMITIVE_SPIN,
            "QUICK_RISE" to VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            "SLOW_RISE" to VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            "QUICK_FALL" to VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            "TICK" to VibrationEffect.Composition.PRIMITIVE_TICK,
            "LOW_TICK" to VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        )
        if (vibrator == null || Build.VERSION.SDK_INT < 30) {
            return primitives.map { NamedEffect(it.first, it.second, "проверка с API 30") }
        }
        return primitives.map { (name, id) ->
            val supported = try {
                vibrator.arePrimitivesSupported(id).firstOrNull() == true
            } catch (error: RuntimeException) {
                return@map NamedEffect(name, id, "ошибка: ${error.message}")
            }
            val duration = if (supported) primitiveDuration(vibrator, id) else 0
            val label = if (supported) "да, оценка API $duration мс" else "нет"
            NamedEffect(name, id, label)
        }
    }

    private fun primitiveDuration(vibrator: Vibrator, primitiveId: Int): Long {
        if (Build.VERSION.SDK_INT < API_MANAGER) return 0L
        return try {
            vibrator.getPrimitiveDurations(primitiveId).firstOrNull()?.toLong() ?: 0L
        } catch (error: RuntimeException) {
            0L
        }
    }

    private fun readPresets(vibrator: Vibrator): String {
        val presets = listOf(
            "CLICK" to VibrationEffect.Preset.PRESET_CLICK,
            "TICK" to VibrationEffect.Preset.PRESET_TICK,
            "LOW_TICK" to VibrationEffect.Preset.PRESET_LOW_TICK,
        )
        return presets.joinToString("; ") { (name, id) ->
            val supported = try {
                vibrator.isPresetSupported(id)
            } catch (error: RuntimeException) {
                return@joinToString "$name: ошибка"
            }
            if (!supported) {
                "$name: нет"
            } else {
                val duration = try {
                    vibrator.getPresetDuration(id).toMillis()
                } catch (error: RuntimeException) {
                    -1L
                }
                "$name: да, оценка $duration мс"
            }
        }
    }

    private fun rejected(message: String) = PlayResult(
        accepted = false,
        message = message,
        commandedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        commandedDurationMs = 0L,
    )
}

enum class DiscriminationKind { WAVEFORM, PRIMITIVE, ENVELOPE }

private fun sdkIntFullOrNull(): Int? {
    return if (Build.VERSION.SDK_INT >= API_ENVELOPE) Build.VERSION.SDK_INT_FULL else null
}

private fun finiteOrNull(value: Float): Float? = if (value.isFinite()) value else null

private fun yesNo(value: Boolean): String = if (value) "да" else "нет"

private fun triState(value: Boolean?): String = when (value) {
    null -> "API недоступен"
    true -> "да"
    false -> "нет"
}

private fun numberOrUnavailable(value: Float?): String {
    return value?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "Не предоставляется устройством"
}

private fun hertz(value: Float?): String {
    return value?.let { String.format(java.util.Locale.US, "%.2f Гц", it) } ?: "Не предоставляется устройством"
}
