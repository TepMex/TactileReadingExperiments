package com.tepmex.hapticlab.core.export

import com.tepmex.hapticlab.core.log.ExperimentSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

fun csvEscape(value: String): String {
    val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuotes) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

fun sessionToCsv(session: ExperimentSession): String = buildString {
    appendLine("record_level,meaning")
    appendLine(
        "A,\"Моменты команд вибрации и событий ввода. Это не физический отклик мотора и не момент ощущения.\"",
    )
    appendLine()
    appendLine("session_id,type,wall_clock_iso,manufacturer,model,android_release,sdk_int,sdk_int_full")
    appendLine(
        listOf(
            session.id,
            session.type,
            session.wallClockIso,
            session.device.manufacturer,
            session.device.model,
            session.device.androidRelease,
            session.device.sdkInt.toString(),
            session.device.sdkIntFull?.toString().orEmpty(),
        ).joinToString(",") { csvEscape(it) },
    )
    appendLine()
    appendLine("parameter,value")
    session.parameters.toSortedMap().forEach { (key, value) ->
        appendLine("${csvEscape(key)},${csvEscape(value)}")
    }
    appendLine()
    appendLine("summary_key,summary_value")
    session.summary.toSortedMap().forEach { (key, value) ->
        appendLine("${csvEscape(key)},${csvEscape(value)}")
    }
    appendLine()
    appendLine(
        "index,elapsed_realtime_ns,motion_event_elapsed_realtime_ns,kind,x_px,y_px,x_mm,speed_mm_s,zone,requested_on,detail",
    )
    session.events.forEachIndexed { index, event ->
        appendLine(
            listOf(
                index.toString(),
                event.elapsedRealtimeNanos.toString(),
                event.motionEventElapsedRealtimeNanos?.toString().orEmpty(),
                event.kind,
                event.xPx?.toString().orEmpty(),
                event.yPx?.toString().orEmpty(),
                event.xMm?.toString().orEmpty(),
                event.speedMmPerSec?.toString().orEmpty(),
                event.zoneIndex?.toString().orEmpty(),
                event.requestedVibrationOn?.toString().orEmpty(),
                event.detail.orEmpty(),
            ).joinToString(",") { csvEscape(it) },
        )
    }
}

fun sessionToJson(session: ExperimentSession): String = exportJson.encodeToString(session)

fun percentText(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    return String.format(java.util.Locale.US, "%.1f%%", value * 100.0)
}
