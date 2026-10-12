package com.hapticlab.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hapticlab.core.alphabet.TactileAlphabet
import com.hapticlab.core.alphabet.demoAlphabet
import com.hapticlab.core.export.sessionToCsv
import com.hapticlab.core.export.sessionToJson
import com.hapticlab.core.log.ExperimentSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class NamedPattern(val name: String, val zones: List<Boolean>)

class LabPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("haptic_lab", Context.MODE_PRIVATE)

    var calibrationScale: Float
        get() = prefs.getFloat(KEY_SCALE, 1f)
        set(value) {
            prefs.edit().putFloat(KEY_SCALE, value).apply()
        }

    var verboseLogging: Boolean
        get() = prefs.getBoolean(KEY_VERBOSE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VERBOSE, value).apply()
        }

    fun loadPatterns(): List<NamedPattern> {
        val raw = prefs.getString(KEY_PATTERNS, null) ?: return emptyList()
        return try {
            AppJson.decodeFromString(raw)
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun savePatterns(patterns: List<NamedPattern>) {
        prefs.edit().putString(KEY_PATTERNS, AppJson.encodeToString(patterns)).apply()
    }

    fun loadAlphabet(): TactileAlphabet {
        val raw = prefs.getString(KEY_ALPHABET, null) ?: return demoAlphabet()
        return try {
            AppJson.decodeFromString(raw)
        } catch (error: Exception) {
            demoAlphabet()
        }
    }

    fun saveAlphabet(alphabet: TactileAlphabet) {
        prefs.edit().putString(KEY_ALPHABET, AppJson.encodeToString(alphabet)).apply()
    }

    private companion object {
        const val KEY_SCALE = "calibration_scale"
        const val KEY_VERBOSE = "verbose_logging"
        const val KEY_PATTERNS = "patterns"
        const val KEY_ALPHABET = "alphabet"
    }
}

class SessionStore(private val context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val exportDir = File(context.filesDir, "exports").apply { mkdirs() }

    fun save(session: ExperimentSession) {
        File(dir, safeName(session.id) + ".json").writeText(sessionToJson(session))
    }

    fun list(): List<ExperimentSession> {
        val files = dir.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        return files.take(80).mapNotNull { file ->
            try {
                AppJson.decodeFromString<ExperimentSession>(file.readText())
            } catch (error: Exception) {
                null
            }
        }
    }

    fun export(session: ExperimentSession): Pair<File, File> {
        exportDir.mkdirs()
        val base = safeName(session.id)
        val csv = File(exportDir, "$base.csv")
        val json = File(exportDir, "$base.json")
        csv.writeText(sessionToCsv(session))
        json.writeText(sessionToJson(session))
        return csv to json
    }

    private fun safeName(id: String): String = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "session" }
}

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Экспорт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
