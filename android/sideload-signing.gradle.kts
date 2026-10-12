import java.io.File
import java.util.Properties

/**
 * Committed sideload keystore signing for GitHub Pages APKs.
 * Apply from app/build.gradle.kts after setting extra["sideloadPropertyPrefix"].
 *
 * Copy android/sideload.keystore and android/sideload-signing.properties into the
 * app directory. Gradle loads them from the app root (rootProject).
 *
 * Prefix examples: ankidroidllm, localtts, chesswatch
 * Optional override in <module>/local.properties: <prefix>.signingStoreFile, etc.
 */
val sideloadPropertyPrefix: String = checkNotNull(
    project.extensions.extraProperties["sideloadPropertyPrefix"] as? String,
) { "Set extra[\"sideloadPropertyPrefix\"] before applying sideload-signing.gradle.kts" }

val autoVersionCode = (System.currentTimeMillis() / 1000L).toInt()

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

fun propLocal(name: String): String? =
    localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val overrideStoreFile = propLocal("$sideloadPropertyPrefix.signingStoreFile")
val overrideStorePassword = propLocal("$sideloadPropertyPrefix.signingStorePassword")
val overrideKeyAlias = propLocal("$sideloadPropertyPrefix.signingKeyAlias")
val overrideKeyPassword = propLocal("$sideloadPropertyPrefix.signingKeyPassword")
val useOverrideSigning = overrideStoreFile != null &&
    overrideStorePassword != null &&
    overrideKeyAlias != null &&
    overrideKeyPassword != null

val sideloadProps = Properties()
val sideloadPropsFile = rootProject.file("sideload-signing.properties")
val sideloadKs = rootProject.file("sideload.keystore")
if (!useOverrideSigning && sideloadPropsFile.exists() && sideloadKs.exists()) {
    sideloadPropsFile.inputStream().use { sideloadProps.load(it) }
}

fun propSideload(name: String): String? =
    sideloadProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val useCommittedSideload = !useOverrideSigning &&
    sideloadKs.exists() &&
    propSideload("storeFile") != null &&
    propSideload("storePassword") != null &&
    propSideload("keyAlias") != null &&
    propSideload("keyPassword") != null

val useCustomSigning: Boolean = useOverrideSigning || useCommittedSideload

val sideloadStoreFile: File? = when {
    !useCustomSigning -> null
    useOverrideSigning -> rootProject.file(overrideStoreFile!!)
    else -> rootProject.file(propSideload("storeFile")!!)
}
val sideloadStorePassword: String? = when {
    !useCustomSigning -> null
    useOverrideSigning -> overrideStorePassword
    else -> propSideload("storePassword")
}
val sideloadKeyAlias: String? = when {
    !useCustomSigning -> null
    useOverrideSigning -> overrideKeyAlias
    else -> propSideload("keyAlias")
}
val sideloadKeyPassword: String? = when {
    !useCustomSigning -> null
    useOverrideSigning -> overrideKeyPassword
    else -> propSideload("keyPassword")
}

with(project.extensions.extraProperties) {
    set("autoVersionCode", autoVersionCode)
    set("useCustomSigning", useCustomSigning)
    set("sideloadStoreFile", sideloadStoreFile)
    set("sideloadStorePassword", sideloadStorePassword)
    set("sideloadKeyAlias", sideloadKeyAlias)
    set("sideloadKeyPassword", sideloadKeyPassword)
}
