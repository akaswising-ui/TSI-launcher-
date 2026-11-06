package com.tsi.launcher.data

data class MinecraftVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String
)

enum class VersionFilter(val label: String) {
    ALL("All versions"),
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
    OLD_ALPHA("Old Alpha"),
    OLD_BETA("Old Beta");

    fun accepts(type: String): Boolean = when (this) {
        ALL -> true
        RELEASE -> type.equals("release", ignoreCase = true)
        SNAPSHOT -> type.equals("snapshot", ignoreCase = true)
        OLD_ALPHA -> type.equals("old_alpha", ignoreCase = true)
        OLD_BETA -> type.equals("old_beta", ignoreCase = true)
    }
}

data class ClientDownloadResult(
    val versionId: String,
    val clientJarPath: String,
    val metadataPath: String,
    val requiredJavaMajor: Int?
)
