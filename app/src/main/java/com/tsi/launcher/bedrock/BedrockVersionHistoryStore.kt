package com.tsi.launcher.bedrock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BedrockHistoryEntry(
    val id: String,
    val eventType: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val selectedTrack: String,
    val timestamp: Long
)

object BedrockVersionHistoryStore {
    const val TRACK_STABLE = "STABLE"
    const val TRACK_BETA = "BETA"
    private const val PREFS = "tsi_bedrock_history"
    private const val KEY_HISTORY = "entries"
    private const val KEY_SELECTED_TRACK = "last_selected_track"
    private const val MAX_ENTRIES = 40

    fun selectedTrack(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TRACK, "NOT SET") ?: "NOT SET"

    fun read(context: Context): List<BedrockHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        BedrockHistoryEntry(
                            id = item.optString("id"),
                            eventType = item.optString("eventType"),
                            packageName = item.optString("packageName"),
                            versionName = item.optString("versionName"),
                            versionCode = item.optLong("versionCode", 0L),
                            selectedTrack = item.optString("selectedTrack", "NOT SET"),
                            timestamp = item.optLong("timestamp", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Stores a local observation only when the installed package version changes.
     * The selected channel is a user preference, not proof of Play beta enrollment.
     */
    fun recordInstalledPackage(
        context: Context,
        status: BedrockInstallStatus
    ): List<BedrockHistoryEntry> {
        if (!status.installed) return read(context)
        val history = read(context)
        val latestObservation = history.firstOrNull { it.eventType == "VERSION_OBSERVED" }
        val sameVersion = latestObservation != null &&
            latestObservation.packageName == status.packageName &&
            latestObservation.versionName == status.versionName &&
            latestObservation.versionCode == status.versionCode
        if (sameVersion) return history

        val entry = BedrockHistoryEntry(
            id = "version-${status.versionCode}-${System.currentTimeMillis()}",
            eventType = "VERSION_OBSERVED",
            packageName = status.packageName ?: BedrockIntegration.OFFICIAL_PACKAGE,
            versionName = status.versionName ?: "Unknown",
            versionCode = status.versionCode ?: 0L,
            selectedTrack = selectedTrack(context),
            timestamp = System.currentTimeMillis()
        )
        return prepend(context, entry)
    }

    /**
     * Records the track the user last chose to manage in Google Play.
     * Android does not expose the active Play testing track to other apps.
     */
    fun selectTrack(context: Context, track: String): List<BedrockHistoryEntry> {
        require(track == TRACK_STABLE || track == TRACK_BETA) {
            "Unsupported Bedrock channel"
        }
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = preferences.getString(KEY_SELECTED_TRACK, "NOT SET") ?: "NOT SET"
        if (previous == track) return read(context)

        preferences.edit().putString(KEY_SELECTED_TRACK, track).apply()
        val status = BedrockIntegration.readInstallStatus(context)
        val entry = BedrockHistoryEntry(
            id = "track-$track-${System.currentTimeMillis()}",
            eventType = "TRACK_SELECTED",
            packageName = status.packageName ?: BedrockIntegration.OFFICIAL_PACKAGE,
            versionName = status.versionName ?: "Not installed",
            versionCode = status.versionCode ?: 0L,
            selectedTrack = track,
            timestamp = System.currentTimeMillis()
        )
        return prepend(context, entry)
    }

    private fun prepend(
        context: Context,
        entry: BedrockHistoryEntry
    ): List<BedrockHistoryEntry> {
        val updated = (listOf(entry) + read(context)).take(MAX_ENTRIES)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("eventType", item.eventType)
                    .put("packageName", item.packageName)
                    .put("versionName", item.versionName)
                    .put("versionCode", item.versionCode)
                    .put("selectedTrack", item.selectedTrack)
                    .put("timestamp", item.timestamp)
            )
        }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .commit()
        check(saved) { "Could not save Bedrock version history" }
        return updated
    }
}
