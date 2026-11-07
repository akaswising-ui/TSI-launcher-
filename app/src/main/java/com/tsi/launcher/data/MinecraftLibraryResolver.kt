package com.tsi.launcher.data

import org.json.JSONObject
import java.io.IOException

/**
 * Resolves Minecraft libraries from both current Mojang metadata (downloads.artifact)
 * and legacy pre-1.13 Maven-coordinate metadata (name + url).
 *
 * Native-only classifier artifacts are deliberately skipped. Amethyst supplies
 * Android-compatible LWJGL/JNA native binaries; desktop native ZIP/JARs must not
 * be put on an Android game classpath.
 */
object MinecraftLibraryResolver {
    data class Artifact(
        val path: String,
        val url: String,
        val sha1: String?,
        val size: Long
    )

    fun resolve(library: JSONObject): Artifact? {
        val downloads = library.optJSONObject("downloads")
        val artifact = downloads?.optJSONObject("artifact")

        if (artifact != null) {
            val path = artifact.optString("path").trim()
            if (path.isBlank()) {
                throw IOException("Library metadata has no artifact path: ${library.optString("name")}")
            }
            val sha1 = artifact.optString("sha1")
                .takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
            val size = artifact.optLong("size", -1L)
            val directUrl = artifact.optString("url").trim()
            val baseUrl = library.optString("url").trim()
                .ifBlank { "https://libraries.minecraft.net/" }
            val url = directUrl.ifBlank { baseUrl.trimEnd('/') + "/" + path }
            return Artifact(path, url, sha1, size)
        }

        // Modern metadata may have classifiers but no main artifact. Those
        // classifier-only entries are native payloads, not Java classpath JARs.
        if (downloads != null) return null

        val rawName = library.optString("name").trim()
        if (rawName.isBlank()) return null
        val nameParts = rawName.split("@", limit = 2)
        val coordinate = nameParts[0]
        val extension = nameParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "jar"
        if (!extension.matches(Regex("[A-Za-z0-9]{1,10}"))) return null

        val parts = coordinate.split(':')
        if (parts.size !in 3..4) return null

        val group = parts[0]
        val module = parts[1]
        val version = parts[2]
        val classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() }

        if (!group.matches(Regex("[A-Za-z0-9_.-]+")) ||
            !module.matches(Regex("[A-Za-z0-9_.-]+")) ||
            !version.matches(Regex("[A-Za-z0-9_.+!-]+")) ||
            (classifier != null && !classifier.matches(Regex("[A-Za-z0-9_.-]+")))
        ) {
            throw IOException("Unsafe legacy Maven coordinate: $rawName")
        }

        // These old entries declare only platform-native payloads. Their
        // Android equivalents are supplied by Amethyst's native runtime.
        val hasNativeRules = library.optJSONObject("natives") != null
        if (classifier?.startsWith("natives-", ignoreCase = true) == true ||
            (hasNativeRules && module.endsWith("-platform", ignoreCase = true))
        ) {
            return null
        }

        val fileName = buildString {
            append(module).append('-').append(version)
            if (classifier != null) append('-').append(classifier)
            append('.').append(extension)
        }
        val path = group.replace('.', '/') + "/" + module + "/" + version + "/" + fileName
        val baseUrl = library.optString("url").trim()
            .ifBlank { "https://libraries.minecraft.net/" }
        val url = baseUrl.trimEnd('/') + "/" + path
        return Artifact(path, url, null, -1L)
    }
}
