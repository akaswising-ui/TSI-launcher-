package com.tsi.launcher.runtime

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DetectedJavaRuntime(
    val name: String,
    val majorVersion: Int,
    val version: String,
    val architecture: String,
    val home: File,
    val jvmLibrary: File
)

data class VersionVmInspection(
    val versionId: String,
    val clientJarExists: Boolean,
    val metadataExists: Boolean,
    val requiredJavaMajor: Int?,
    val libraryCount: Int,
    val installedLibraryCount: Int,
    val assetIndexId: String?,
    val assetIndexExists: Boolean,
    val assetObjectCount: Int,
    val compatibleRuntime: DetectedJavaRuntime?,
    val detectedRuntimes: List<DetectedJavaRuntime>,
    val engineBridgeAvailable: Boolean,
    val blockers: List<String>
) {
    val readyToLaunch: Boolean get() = blockers.isEmpty()
}

/**
 * Inspects the app-specific Java runtime layout used by TSI.
 *
 * Runtime directory contract:
 * .minecraft/runtimes/<runtime-name>/release
 * .minecraft/runtimes/<runtime-name>/lib/server/libjvm.so
 *
 * A detected JRE is not by itself an Android Minecraft engine. The JNI/JVM,
 * LWJGL, renderer, surface and input bridge must also be linked.
 */
class JavaRuntimeManager(context: Context) {
    private val root = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        ".minecraft"
    )

    fun inspect(versionId: String, runtimePreference: String): VersionVmInspection {
        val safeId = safeSegment(versionId)
        val versionFolder = File(File(root, "versions"), safeId)
        val clientJar = File(versionFolder, "$safeId.jar")
        val metadataFile = File(versionFolder, "$safeId.json")
        val librariesRoot = File(root, "libraries")
        val assetsRoot = File(root, "assets")

        var requiredMajor: Int? = null
        var libraryCount = 0
        var installedLibraryCount = 0
        var assetIndexId: String? = null
        var assetIndexExists = false

        if (metadataFile.isFile) {
            try {
                val metadata = JSONObject(metadataFile.readText(Charsets.UTF_8))
                requiredMajor = metadata.optJSONObject("javaVersion")
                    ?.optInt("majorVersion")
                    ?.takeIf { it in 8..99 }

                // Legacy metadata before javaVersion used Java 8.
                if (requiredMajor == null && isLegacyVersion(safeId)) {
                    requiredMajor = 8
                }

                val libraries = metadata.optJSONArray("libraries")
                if (libraries != null) {
                    for (index in 0 until libraries.length()) {
                        val library = libraries.optJSONObject(index) ?: continue
                        if (!libraryAllowed(library)) continue
                        val downloads = library.optJSONObject("downloads") ?: continue
                        val artifact = downloads.optJSONObject("artifact") ?: continue
                        val relativePath = artifact.optString("path")
                        if (relativePath.isBlank()) continue

                        libraryCount++
                        val local = File(librariesRoot, relativePath)
                        if (local.isFile) installedLibraryCount++
                    }
                }

                assetIndexId = metadata.optJSONObject("assetIndex")
                    ?.optString("id")
                    ?.takeIf { !it.isNullOrBlank() }

                if (assetIndexId != null) {
                    assetIndexExists = File(
                        File(assetsRoot, "indexes"),
                        "$assetIndexId.json"
                    ).isFile
                }
            } catch (_: Exception) {
                // Missing or corrupt metadata is reported in blockers below.
            }
        }

        val detected = scanRuntimes()
        val requestedMajor = runtimePreference
            .removePrefix("Java")
            .trim()
            .toIntOrNull()
        val targetMajor = requestedMajor ?: requiredMajor
        val compatible = detected.firstOrNull { it.majorVersion == targetMajor }

        val objectDirectory = File(assetsRoot, "objects")
        val objectCount = objectDirectory.listFiles()?.count { it.isDirectory } ?: 0

        val blockers = buildList {
            if (!clientJar.isFile) add("Client JAR is missing for $safeId.")
            if (!metadataFile.isFile) add("Version metadata JSON is missing.")
            if (requiredMajor == null) add("Java major version could not be resolved from version metadata.")
            if (targetMajor != null && compatible == null) {
                add("No Android JRE for Java $targetMajor is installed in TSI's runtimes folder.")
            }
            if (libraryCount == 0 || installedLibraryCount < libraryCount) {
                add("Minecraft libraries are incomplete ($installedLibraryCount/$libraryCount).")
            }
            if (assetIndexId == null || !assetIndexExists) {
                add("The Minecraft asset index is missing.")
            }
            if (objectCount == 0) add("Minecraft asset objects have not been installed.")
            add("The Android JVM/LWJGL renderer bridge is not linked into TSI yet.")
        }

        return VersionVmInspection(
            versionId = safeId,
            clientJarExists = clientJar.isFile,
            metadataExists = metadataFile.isFile,
            requiredJavaMajor = requiredMajor,
            libraryCount = libraryCount,
            installedLibraryCount = installedLibraryCount,
            assetIndexId = assetIndexId,
            assetIndexExists = assetIndexExists,
            assetObjectCount = objectCount,
            compatibleRuntime = compatible,
            detectedRuntimes = detected,
            engineBridgeAvailable = false,
            blockers = blockers
        )
    }

    fun runtimeDirectory(): File = File(root, "runtimes")

    private fun scanRuntimes(): List<DetectedJavaRuntime> {
        val entries = runtimeDirectory().listFiles()?.filter { it.isDirectory } ?: return emptyList()

        return entries.mapNotNull { directory ->
            val homeCandidates = listOf(directory, File(directory, "jre"))
            homeCandidates.firstNotNullOfOrNull { home ->
                val releaseFile = File(home, "release")
                if (!releaseFile.isFile) return@firstNotNullOfOrNull null

                val properties = parseRelease(releaseFile)
                val versionText = properties["JAVA_VERSION"].orEmpty().trim('"')
                val major = parseJavaMajor(versionText) ?: return@firstNotNullOfOrNull null
                val arch = properties["OS_ARCH"].orEmpty().trim('"')

                val jvmCandidates = listOf(
                    File(home, "lib/server/libjvm.so"),
                    File(home, "lib/client/libjvm.so"),
                    File(home, "lib/amd64/server/libjvm.so"),
                    File(home, "lib/amd64/client/libjvm.so"),
                    File(home, "lib/aarch64/server/libjvm.so"),
                    File(home, "lib/aarch64/client/libjvm.so"),
                    File(home, "lib/arm/server/libjvm.so"),
                    File(home, "lib/arm/client/libjvm.so"),
                    File(home, "lib/x86_64/server/libjvm.so"),
                    File(home, "lib/x86/server/libjvm.so")
                )
                val jvm = jvmCandidates.firstOrNull { it.isFile }
                    ?: return@firstNotNullOfOrNull null

                if (!isDeviceCompatible(arch)) return@firstNotNullOfOrNull null

                DetectedJavaRuntime(
                    name = directory.name,
                    majorVersion = major,
                    version = versionText,
                    architecture = arch,
                    home = home,
                    jvmLibrary = jvm
                )
            }
        }.sortedBy { it.majorVersion }
    }

    private fun libraryAllowed(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val architecture = if (abi.contains("64")) "x86_64" else "x86"

        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index) ?: continue
            val os = rule.optJSONObject("os")
            val osMatches = if (os == null) true else {
                val nameMatches = os.optString("name").let { it.isBlank() || it == "linux" }
                val archPattern = os.optString("arch")
                val archMatches = archPattern.isBlank() || runCatching {
                    Regex(archPattern).containsMatchIn(architecture)
                }.getOrDefault(false)
                val versionPattern = os.optString("version")
                val versionMatches = versionPattern.isBlank() || runCatching {
                    Regex(versionPattern).containsMatchIn(System.getProperty("os.version").orEmpty())
                }.getOrDefault(false)
                nameMatches && archMatches && versionMatches
            }
            val features = rule.optJSONObject("features")
            val featuresMatch = if (features == null) true else {
                val keys = features.keys()
                var matches = true
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (features.optBoolean(key, false)) matches = false
                }
                matches
            }
            if (osMatches && featuresMatch) {
                allowed = rule.optString("action") == "allow"
            }
        }
        return allowed
    }

    private fun parseRelease(file: File): Map<String, String> {
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
    }

    private fun parseJavaMajor(version: String): Int? {
        val normalized = version.removePrefix("1.")
        return normalized.substringBefore('.').substringBefore('-').toIntOrNull()
    }

    private fun isDeviceCompatible(runtimeArch: String): Boolean {
        val arch = runtimeArch.lowercase(Locale.ROOT)
        val deviceAbis = Build.SUPPORTED_ABIS.map { it.lowercase(Locale.ROOT) }
        return when {
            arch.contains("aarch64") || arch.contains("arm64") ->
                deviceAbis.any { it.contains("arm64") }
            arch == "arm" || arch.contains("armv7") ->
                deviceAbis.any { it.contains("armeabi") }
            arch.contains("x86_64") || arch.contains("amd64") ->
                deviceAbis.any { it.contains("x86_64") }
            arch == "x86" || arch.contains("i386") || arch.contains("i686") ->
                deviceAbis.any { it == "x86" || it.contains("x86") }
            else -> false
        }
    }

    private fun isLegacyVersion(versionId: String): Boolean {
        val match = Regex("^(?:1\\.(\\d+))(?:\\.|$)").find(versionId) ?: return false
        val minor = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return false
        return minor <= 16
    }

    private fun safeSegment(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") {
            "Invalid Minecraft version ID"
        }
        return safe
    }
}
