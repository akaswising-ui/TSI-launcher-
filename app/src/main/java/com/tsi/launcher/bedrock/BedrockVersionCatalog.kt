package com.tsi.launcher.bedrock

import org.json.JSONArray
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BedrockChannel(val label: String) {
    ALL("All"),
    VANILLA("Vanilla"),
    PREVIEW("Preview"),
    BETA("Beta")
}

data class BedrockVersionEntry(
    val id: String,
    val name: String,
    val channel: BedrockChannel,
    val badge: String,
    val versionLabel: String,
    val sizeLabel: String,
    val description: String,
    val actionLabel: String,
    val actionUrl: String,
    val enabled: Boolean = true
)

object BedrockVersionCatalog {
    // Metadata only. Google Play owns game binary delivery and signature validation.
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/akaswising-ui/TSI-launcher-/main/bedrock/versions.json"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private val allowedHosts = setOf(
        "play.google.com",
        "help.minecraft.net",
        "www.minecraft.net"
    )

    suspend fun fetch(): List<BedrockVersionEntry> = withContext(Dispatchers.IO) {
        val connection = (URL(CATALOG_URL).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TSI-Launcher-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Catalog server returned HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(JSONArray(json))
        } finally {
            connection.disconnect()
        }
    }

    fun parse(array: JSONArray): List<BedrockVersionEntry> {
        val entries = ArrayList<BedrockVersionEntry>(array.length())
        val seenIds = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optString("id").trim()
            if (id.isBlank() || !seenIds.add(id)) continue

            val channelName = item.optString("channel").uppercase()
            val channel = BedrockChannel.values().firstOrNull {
                it.name == channelName && it != BedrockChannel.ALL
            } ?: continue

            val url = item.optString("actionUrl").trim()
            val parsedUrl = runCatching { URL(url) }.getOrNull() ?: continue
            if (parsedUrl.protocol != "https" || parsedUrl.host.lowercase() !in allowedHosts) {
                continue
            }

            val name = item.optString("name").trim()
            if (name.isBlank()) continue

            entries += BedrockVersionEntry(
                id = id,
                name = name,
                channel = channel,
                badge = item.optString("badge", channel.label),
                versionLabel = item.optString("versionLabel", "Managed by official channel"),
                sizeLabel = item.optString("sizeLabel", "Size shown by Google Play"),
                description = item.optString("description"),
                actionLabel = item.optString("actionLabel", "Open official information"),
                actionUrl = url,
                enabled = item.optBoolean("enabled", true)
            )
        }
        return entries
    }

    fun builtInFallback(): List<BedrockVersionEntry> = listOf(
        BedrockVersionEntry(
            id = "stable",
            name = "Minecraft Bedrock for Android",
            channel = BedrockChannel.VANILLA,
            badge = "STABLE",
            versionLabel = "Installed package: com.mojang.minecraftpe",
            sizeLabel = "Install size shown by Google Play",
            description = "The official stable Android app. TSI detects its installed version and can launch it.",
            actionLabel = "OPEN GOOGLE PLAY",
            actionUrl = "https://play.google.com/store/apps/details?id=com.mojang.minecraftpe"
        ),
        BedrockVersionEntry(
            id = "beta",
            name = "Minecraft Android Beta",
            channel = BedrockChannel.BETA,
            badge = "BETA TRACK",
            versionLabel = "Uses the same Android app package as Stable",
            sizeLabel = "Update size shown by Google Play",
            description = "Joining Beta replaces the normal Minecraft client. Android does not install Stable and Beta side-by-side.",
            actionLabel = "OPEN BETA ENROLLMENT",
            actionUrl = "https://play.google.com/apps/testing/com.mojang.minecraftpe"
        ),
        BedrockVersionEntry(
            id = "preview",
            name = "Minecraft Preview",
            channel = BedrockChannel.PREVIEW,
            badge = "NOT A SEPARATE ANDROID APP",
            versionLabel = "Android development builds use the Beta track",
            sizeLabel = "No separate Android package",
            description = "Mojang's mobile documentation describes Beta enrollment on Android. The separate Preview app flow is not offered as an Android package.",
            actionLabel = "ANDROID BETA / PREVIEW INFO",
            actionUrl = "https://help.minecraft.net/hc/en-us/articles/4423653831821"
        )
    )
}
