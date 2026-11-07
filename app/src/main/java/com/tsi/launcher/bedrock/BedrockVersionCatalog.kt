package com.tsi.launcher.bedrock

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
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
    // The JSON is version metadata only. Game binaries remain distributed and
    // installed by the official Google Play / Minecraft channels.
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/akaswising-ui/TSI-launcher-/main/bedrock/versions.json"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    suspend fun fetch(context: Context): List<BedrockVersionEntry> =
        withContext(Dispatchers.IO) {
            val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
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
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val channel = runCatching {
                BedrockChannel.valueOf(item.getString("channel").uppercase())
            }.getOrElse { BedrockChannel.VANILLA }
            val url = item.optString("actionUrl")
            if (!url.startsWith("https://play.google.com/") &&
                !url.startsWith("https://www.minecraft.net/")) {
                continue
            }
            entries += BedrockVersionEntry(
                id = item.getString("id"),
                name = item.getString("name"),
                channel = channel,
                badge = item.optString("badge", channel.label),
                versionLabel = item.optString("versionLabel", "Managed by official channel"),
                sizeLabel = item.optString("sizeLabel", "Size shown by Google Play"),
                description = item.optString("description"),
                actionLabel = item.optString("actionLabel", "Open official page"),
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
            versionLabel = "Latest stable release",
            sizeLabel = "Size shown by Google Play",
            description = "Install and update the licensed game through Google Play.",
            actionLabel = "OPEN GOOGLE PLAY",
            actionUrl = "https://play.google.com/store/apps/details?id=com.mojang.minecraftpe"
        ),
        BedrockVersionEntry(
            id = "preview",
            name = "Minecraft Preview",
            channel = BedrockChannel.PREVIEW,
            badge = "PREVIEW",
            versionLabel = "Latest available preview",
            sizeLabel = "Size shown by Google Play",
            description = "Availability depends on region and the official Preview rollout.",
            actionLabel = "CHECK PREVIEW",
            actionUrl = "https://play.google.com/store/apps/details?id=com.mojang.minecraftpreview"
        ),
        BedrockVersionEntry(
            id = "beta",
            name = "Android Beta Program",
            channel = BedrockChannel.BETA,
            badge = "BETA",
            versionLabel = "Current Play testing channel",
            sizeLabel = "Managed by Google Play",
            description = "Join or leave the official testing track using the Google account that owns Minecraft.",
            actionLabel = "OPEN BETA PROGRAM",
            actionUrl = "https://play.google.com/apps/testing/com.mojang.minecraftpe"
        )
    )
}
