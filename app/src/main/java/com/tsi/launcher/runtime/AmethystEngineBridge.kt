package com.tsi.launcher.runtime

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Adapter from TSI's installed game/runtime layout to Amethyst's Android game host.
 *
 * Amethyst is built into the same APK by scripts/integrate-amethyst-engine.sh.
 * Its Tools.DIR_GAME_HOME resolves to context.getExternalFilesDir(null), so
 * TSI's .minecraft directory and Amethyst's .minecraft directory are the same.
 * Its MultiRT_HOME resolves to context.filesDir.parentFile/runtimes, which is
 * also where AndroidJreInstaller installs the Android OpenJDK packages.
 */
object AmethystEngineBridge {
    private const val LAUNCHER_ACTIVITY = "net.kdt.pojavlaunch.MainActivity"
    private const val PROFILE_PREFS = "pojav_profile"
    private const val PROFILE_PREF_FILE = "file"
    private const val PROFILE_PREFIX = "amethyst://"

    private val requiredJavaClasses = listOf(
        LAUNCHER_ACTIVITY,
        "org.libsdl.app.SDL",
        "org.lwjgl.glfw.CallbackBridge",
        "org.lwjgl.opengl.GL",
        "org.lwjgl.openal.AL10"
    )

    // These are loaded by Amethyst's own JVM startup path (JREUtils).
    private val requiredNativeLibraries = listOf(
        "libpojavexec.so",
        "libpojavexec_awt.so",
        "libexithook.so"
    )

    fun isAvailable(context: Context): Boolean = missingComponents(context).isEmpty()

    fun missingComponents(context: Context): List<String> {
        val missing = mutableListOf<String>()
        requiredJavaClasses.forEach { className ->
            val available = runCatching {
                Class.forName(className, false, context.classLoader)
            }.isSuccess
            if (!available) missing += "Amethyst class: $className"
        }

        val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
        requiredNativeLibraries.forEach { library ->
            if (!File(nativeDirectory, library).isFile) {
                missing += "Amethyst native library: $library"
            }
        }
        return missing
    }

    suspend fun prepare(
        context: Context,
        progress: suspend (String) -> Unit
    ) {
        progress("Checking the embedded Amethyst JVM and native launcher…")
        val missing = missingComponents(context)
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "The Amethyst engine bundle is incomplete: " + missing.joinToString()
            )
        }
        progress("Amethyst JVM, SDL, LWJGL, OpenGL and OpenAL classes are present.")
    }

    fun launch(
        context: Context,
        versionId: String,
        heapMb: Float,
        runtime: DetectedJavaRuntime,
        playerName: String
    ) {
        val safeVersion = versionId.trim()
        require(safeVersion.matches(Regex("[A-Za-z0-9._+\\-]{1,100}"))) {
            "Invalid Minecraft version identifier."
        }
        require(runtime.name.matches(Regex("[A-Za-z0-9._+\\-]{1,80}"))) {
            "Invalid Android runtime name."
        }
        require(runtime.jvmLibrary.isFile && File(runtime.home, "release").isFile) {
            "The selected Android JRE is incomplete."
        }

        val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val minecraftRoot = File(externalRoot, ".minecraft")
        val versionDirectory = File(File(minecraftRoot, "versions"), safeVersion)
        require(File(versionDirectory, "$safeVersion.json").isFile) {
            "Minecraft $safeVersion version metadata is missing."
        }
        require(File(versionDirectory, "$safeVersion.jar").isFile) {
            "Minecraft $safeVersion client JAR is missing."
        }

        val gameDirectory = File(
            File(File(minecraftRoot, "instances"), safeVersion),
            "game"
        )
        if (!gameDirectory.exists() && !gameDirectory.mkdirs()) {
            throw IOException("Could not create the selected Minecraft game directory.")
        }

        val safePlayer = playerName
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
            .take(16)
            .let { if (it.length in 3..16) it else "Steve" }

        writeAmethystProfile(
            context = context,
            minecraftRoot = minecraftRoot,
            versionId = safeVersion,
            runtimeName = runtime.name,
            playerName = safePlayer,
            heapMb = heapMb.toInt().coerceIn(512, 16384)
        )

        val launchIntent = Intent(
            context,
            net.kdt.pojavlaunch.MainActivity::class.java
        ).putExtra("intent_version", safeVersion)

        if (context !is Activity) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }

    private fun writeAmethystProfile(
        context: Context,
        minecraftRoot: File,
        versionId: String,
        runtimeName: String,
        playerName: String,
        heapMb: Int
    ) {
        val profilesFile = File(minecraftRoot, "launcher_profiles.json")
        val launcherProfiles = if (profilesFile.isFile) {
            try {
                JSONObject(profilesFile.readText(Charsets.UTF_8))
            } catch (error: Exception) {
                throw IOException("Amethyst launcher_profiles.json is invalid; existing data was not overwritten.", error)
            }
        } else {
            JSONObject()
        }

        val profiles = launcherProfiles.optJSONObject("profiles") ?: JSONObject()
        val profileKey = UUID.nameUUIDFromBytes(
            "tsi|$versionId|$playerName".toByteArray(Charsets.UTF_8)
        ).toString()

        val profile = JSONObject()
            .put("name", "TSI $versionId")
            .put("type", "custom")
            .put("lastVersionId", versionId)
            .put("gameDir", PROFILE_PREFIX + ".minecraft/instances/$versionId/game")
            .put("javaDir", PROFILE_PREFIX + runtimeName)

        profiles.put(profileKey, profile)
        launcherProfiles.put("profiles", profiles)
        writeAtomically(profilesFile, launcherProfiles.toString(2))

        // Amethyst stores its local/offline account separately from launcher_profiles.json.
        val dataRoot = context.filesDir.parentFile ?: context.filesDir
        val accountsDirectory = File(dataRoot, "accounts")
        if (!accountsDirectory.exists() && !accountsDirectory.mkdirs()) {
            throw IOException("Could not create Amethyst's local account directory.")
        }
        val accountFile = File(accountsDirectory, "$playerName.json")
        val account = JSONObject()
            .put("accessToken", "0")
            .put("clientToken", "0")
            .put("profileId", "00000000-0000-0000-0000-000000000000")
            .put("username", playerName)
            .put("isMicrosoft", false)
        writeAtomically(accountFile, account.toString(2))

        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("currentProfile", profileKey)
            .putString("defaultRuntime", runtimeName)
            .putInt("allocation", heapMb)
            .apply()

        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PROFILE_PREF_FILE, playerName)
            .apply()
    }

    private fun writeAtomically(destination: File, content: String) {
        val parentDirectory = destination.parentFile
            ?: throw IOException("Destination has no parent directory.")
        if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
            throw IOException("Could not create destination directory.")
        }

        val temporary = File(parentDirectory, destination.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            throw IOException("Could not atomically update destination file.")
        }
    }
}
