package com.tepmex.hapticlab.core.psych

import kotlin.math.sqrt
import kotlin.random.Random

data class TrialSpec(
    val index: Int,
    val gapMs: Int?,
    val isControl: Boolean,
)

data class TrialAnswer(
    val spec: TrialSpec,
    val respondedTwo: Boolean,
) {
    val correct: Boolean = if (spec.isControl) !respondedTwo else respondedTwo
}

data class ProportionEstimate(
    val k: Int,
    val n: Int,
    val proportion: Double,
    val lower95: Double,
    val upper95: Double,
)

/**
 * 95% доверительный интервал Уилсона для доли правильных ответов.
 * Это статистика ответов человека, не измерение мотора.
 */
fun wilson95(k: Int, n: Int, z: Double = 1.959963984540054): ProportionEstimate? {
    if (n <= 0) return null
    val successes = k.coerceIn(0, n)
    val phat = successes.toDouble() / n.toDouble()
    val z2 = z * z
    val denom = 1.0 + z2 / n
    val center = (phat + z2 / (2.0 * n)) / denom
    val margin = z * sqrt((phat * (1.0 - phat) / n) + z2 / (4.0 * n * n)) / denom
    return ProportionEstimate(
        k = successes,
        n = n,
        proportion = phat,
        lower95 = (center - margin).coerceIn(0.0, 1.0),
        upper95 = (center + margin).coerceIn(0.0, 1.0),
    )
}

val STANDARD_GAPS_MS: List<Int> = listOf(10, 15, 20, 30, 40, 50, 75, 100, 150)

fun buildConstantStimuliSchedule(
    gapsMs: List<Int> = STANDARD_GAPS_MS,
    repetitions: Int,
    random: Random,
): List<TrialSpec> {
    require(repetitions in 1..50)
    require(gapsMs.isNotEmpty())
    val trials = ArrayList<TrialSpec>()
    for (gap in gapsMs) {
        repeat(repetitions) {
            trials += TrialSpec(index = 0, gapMs = gap, isControl = false)
        }
    }
    repeat(repetitions) {
        trials += TrialSpec(index = 0, gapMs = null, isControl = true)
    }
    trials.shuffle(random)
    return trials.mapIndexed { index, trial -> trial.copy(index = index) }
}

data class IntervalRow(
    val gapMs: Int?,
    val label: String,
    val trials: Int,
    val correct: Int,
    val estimate: ProportionEstimate?,
)

data class PsychSummary(
    val rows: List<IntervalRow>,
    val falseTwoRate: ProportionEstimate?,
    val controlTrials: Int,
    val controlFalseTwos: Int,
)

fun summarize(answers: List<TrialAnswer>, gapOrder: List<Int> = STANDARD_GAPS_MS): PsychSummary {
    val groups = LinkedHashMap<Int?, MutableList<TrialAnswer>>()
    for (gap in gapOrder) groups[gap] = mutableListOf()
    groups[null] = mutableListOf()
    for (answer in answers) {
        val key = if (answer.spec.isControl) null else answer.spec.gapMs
        groups.getOrPut(key) { mutableListOf() }.add(answer)
    }
    val rows = groups.mapNotNull { (gap, list) ->
        if (list.isEmpty()) return@mapNotNull null
        val correct = list.count { it.correct }
        IntervalRow(
            gapMs = gap,
            label = if (gap == null) "Контроль (один импульс)" else "$gap мс",
            trials = list.size,
            correct = correct,
            estimate = wilson95(correct, list.size),
        )
    }
    val controls = answers.filter { it.spec.isControl }
    val falseTwos = controls.count { it.respondedTwo }
    return PsychSummary(
        rows = rows,
        falseTwoRate = wilson95(falseTwos, controls.size),
        controlTrials = controls.size,
        controlFalseTwos = falseTwos,
    )
}

/**
 * Адаптивный подбор интервала, 2-вниз / 1-вверх.
 * Порог — среднее последних разворотов. Это поведенческая оценка, не физический зазор.
 * Контрольные пробы в лестницу не подаются.
 */
class AdaptiveStaircase(
    val levels: List<Int>,
    startIndex: Int = levels.indexOf(100).let { if (it >= 0) it else levels.size / 2 },
) {
    init {
        require(levels.isNotEmpty())
    }

    var index: Int = startIndex.coerceIn(0, levels.lastIndex)
        private set
    var consecutiveCorrect: Int = 0
        private set
    private var lastDirection: Int = 0
    val reversals: List<Int> get() = reversalValues.toList()
    private val reversalValues = ArrayList<Int>()

    val currentGapMs: Int get() = levels[index]

    fun onSignalAnswer(correct: Boolean) {
        val direction = if (correct) {
            consecutiveCorrect += 1
            if (consecutiveCorrect >= 2) {
                consecutiveCorrect = 0
                -1
            } else {
                0
            }
        } else {
            consecutiveCorrect = 0
            1
        }
        if (direction == 0) return
        if (lastDirection != 0 && direction != lastDirection) {
            reversalValues += levels[index]
        }
        lastDirection = direction
        index = (index + direction).coerceIn(0, levels.lastIndex)
    }

    fun thresholdMs(): Double? {
        if (reversalValues.size < 4) return null
        return reversalValues.takeLast(minOf(6, reversalValues.size)).average()
    }
}

fun summaryFields(summary: PsychSummary, thresholdMs: Double? = null): Map<String, String> {
    val fields = linkedMapOf<String, String>()
    var trials = 0
    var correct = 0
    summary.rows.forEach { row ->
        trials += row.trials
        correct += row.correct
        val key = if (row.gapMs == null) "control" else "gap_${row.gapMs}"
        fields["${key}_trials"] = row.trials.toString()
        fields["${key}_correct"] = row.correct.toString()
        fields["${key}_proportion"] = row.estimate?.proportion?.toString().orEmpty()
        fields["${key}_wilson95_low"] = row.estimate?.lower95?.toString().orEmpty()
        fields["${key}_wilson95_high"] = row.estimate?.upper95?.toString().orEmpty()
    }
    fields["trials"] = trials.toString()
    fields["correct"] = correct.toString()
    fields["false_two_count"] = summary.controlFalseTwos.toString()
    fields["false_two_rate"] = summary.falseTwoRate?.proportion?.toString().orEmpty()
    if (thresholdMs != null) fields["adaptive_threshold_ms"] = thresholdMs.toString()
    return fields
}

fun shouldInsertControl(random: Random, probability: Double = 0.25): Boolean {
    val p = probability.coerceIn(0.0, 1.0)
    return random.nextDouble() < p
}
