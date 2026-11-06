package com.tsi.launcher.runtime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.io.File
import java.util.UUID
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TSI adapter for the Android-native Amethyst game engine.
 *
 * This class intentionally uses reflection so the normal TSI launcher source
 * can still be compiled in isolation. The engine-integrated APK includes the
 * pinned Amethyst source, native libraries and activities in the same app.
 */
object AmethystEngineBridge {
    private const val TOOLS_CLASS = "net.kdt.pojavlaunch.Tools"
    private const val GAME_ACTIVITY_CLASS = "net.kdt.pojavlaunch.MainActivity"
    private const val LAUNCHER_PROFILES_CLASS =
        "net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles"
    private const val MINECRAFT_PROFILE_CLASS =
        "net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile"
    private const val MINECRAFT_ACCOUNT_CLASS = "net.kdt.pojavlaunch.value.MinecraftAccount"
    private const val POJAV_PROFILE_CLASS = "net.kdt.pojavlaunch.PojavProfile"
    private const val LAUNCHER_PREFERENCES_CLASS =
        "net.kdt.pojavlaunch.prefs.LauncherPreferences"
    private const val VERSION_EXTRA = "intent_version"

    data class EngineProbe(
        val available: Boolean,
        val details: String
    )

    fun probe(context: Context): EngineProbe {
        val requiredClasses = listOf(
            GAME_ACTIVITY_CLASS,
            TOOLS_CLASS,
            "com.oracle.dalvik.VMLauncher",
            "org.lwjgl.glfw.CallbackBridge",
            "org.libsdl.app.SDL",
            "net.kdt.pojavlaunch.MinecraftGLSurface",
            LAUNCHER_PROFILES_CLASS,
            MINECRAFT_PROFILE_CLASS,
            MINECRAFT_ACCOUNT_CLASS,
            POJAV_PROFILE_CLASS,
            LAUNCHER_PREFERENCES_CLASS
        )

        for (className in requiredClasses) {
            try {
                Class.forName(className, false, context.classLoader)
            } catch (_: Throwable) {
                return EngineProbe(
                    available = false,
                    details = "Missing engine class: $className"
                )
            }
        }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val requiredNative = listOf("libpojavexec.so")
        val missingNative = requiredNative.filterNot { File(nativeDir, it).isFile }
        if (missingNative.isNotEmpty()) {
            return EngineProbe(
                available = false,
                details = "Missing Amethyst native engine library: ${missingNative.joinToString()}"
            )
        }

        return EngineProbe(
            available = true,
            details = "Amethyst JVM, LWJGL, SDL and native launch components detected."
        )
    }

    fun isAvailable(context: Context): Boolean = probe(context).available

    /**
     * Unpack Amethyst's bundled LWJGL 2/3 jars, Android native libraries,
     * renderer helpers and support components before the game activity starts.
     * Amethyst performs these copies on its own executor, so wait for the
     * expected files instead of racing the JVM startup.
     */
    suspend fun prepare(
        context: Context,
        onProgress: suspend (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val check = probe(context)
        check(check.available) { check.details }

        val toolsClass = Class.forName(TOOLS_CLASS)
        val gameHome = File(toolsClass.getField("DIR_GAME_HOME").get(null) as String)
        val privateData = File(toolsClass.getField("DIR_DATA").get(null) as String)
        val asyncAssets = Class.forName("net.kdt.pojavlaunch.tasks.AsyncAssetManager")

        onProgress("Preparing Amethyst runtime components…")
        asyncAssets.getMethod("unpackSingleFiles", Context::class.java).invoke(null, context)
        asyncAssets.getMethod("unpackComponents", Context::class.java).invoke(null, context)

        val abi = when {
            Build.SUPPORTED_ABIS.any { it.contains("arm64") } -> "arm64-v8a"
            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" || it == "armeabi" } -> "armeabi-v7a"
            Build.SUPPORTED_ABIS.any { it.contains("x86_64") } -> "x86_64"
            Build.SUPPORTED_ABIS.any { it == "x86" } -> "x86"
            else -> throw IllegalStateException("No Amethyst LWJGL native bundle for this Android ABI.")
        }

        val expectedFiles = listOf(
            File(gameHome, "caciocavallo/version"),
            File(gameHome, "caciocavallo17/version"),
            File(gameHome, "lwjgl3/3.3.3/lwjgl.jar"),
            File(gameHome, "lwjgl3/3.3.3/lwjgl-3.3.3-merged-modules.jar"),
            File(gameHome, "lwjgl3/3.3.3/lwjgl-lwjglx.jar"),
            File(gameHome, "lwjgl3/3.4.1/lwjgl.jar"),
            File(gameHome, "lwjgl3/3.4.1/lwjgl-3.4.1-merged-modules.jar"),
            File(gameHome, "lwjgl3/3.4.1/lwjgl-lwjglx.jar"),
            File(privateData, "MioLibPatcher/MioLibPatcher.jar"),
            File(privateData, "security/version")
        )
        val nativeDirectories = listOf(
            File(privateData, "lwjgl-3.3.3-natives/$abi"),
            File(privateData, "lwjgl-3.4.1-natives/$abi")
        )

        val deadline = System.currentTimeMillis() + 120_000L
        while (true) {
            val filesReady = expectedFiles.all { it.isFile && it.length() > 0L }
            val nativesReady = nativeDirectories.all { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            if (filesReady && nativesReady) break

            if (System.currentTimeMillis() >= deadline) {
                val missing = (expectedFiles.filterNot { it.isFile && it.length() > 0L } +
                    nativeDirectories.filterNot { it.isDirectory && it.listFiles()?.isNotEmpty() == true })
                    .joinToString { it.absolutePath }
                throw IllegalStateException("Amethyst component extraction did not finish. Missing: $missing")
            }
            onProgress("Extracting LWJGL, renderer and Android native components…")
            Thread.sleep(350L)
        }
        onProgress("Amethyst engine components are ready.")
    }

    fun launch(
        context: Context,
        versionId: String,
        heapMb: Int,
        runtime: DetectedJavaRuntime,
        playerName: String
    ) {
        require(heapMb in 512..4096) { "RAM request must be between 512 MB and 4096 MB." }

        val probe = probe(context)
        check(probe.available) { probe.details }

        val safeVersionId = safeSegment(versionId)
        val tsiRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, ".minecraft")
            .canonicalFile

        val toolsClass = Class.forName(TOOLS_CLASS)
        val engineMinecraftRoot = File(
            toolsClass.getField("DIR_GAME_NEW").get(null) as String
        ).canonicalFile
        check(engineMinecraftRoot == tsiRoot) {
            "TSI and Amethyst use different Minecraft storage roots. Refusing to launch an incomplete copy."
        }

        val versionsDirectory = File(tsiRoot, "versions/$safeVersionId")
        val clientJar = File(versionsDirectory, "$safeVersionId.jar")
        val metadataJson = File(versionsDirectory, "$safeVersionId.json")
        check(clientJar.isFile && metadataJson.isFile) {
            "The selected version is not fully installed in TSI storage."
        }

        val multiRuntimeHome = File(
            toolsClass.getField("MULTIRT_HOME").get(null) as String
        ).canonicalFile
        val expectedRuntimeDirectory = File(multiRuntimeHome, runtime.name).canonicalFile
        check(expectedRuntimeDirectory.isDirectory &&
            File(expectedRuntimeDirectory, "release").isFile &&
            runtime.jvmLibrary.isFile
        ) {
            "Java ${runtime.majorVersion} is not installed in Amethyst's MultiRT runtime directory."
        }

        configureOfflineProfile(
            context = context,
            toolsClass = toolsClass,
            versionId = safeVersionId,
            heapMb = heapMb,
            runtimeName = runtime.name,
            playerName = playerName
        )

        val gameActivity = Class.forName(GAME_ACTIVITY_CLASS)
        val intent = Intent(context, gameActivity).apply {
            putExtra(VERSION_EXTRA, safeVersionId)
        }
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun configureOfflineProfile(
        context: Context,
        toolsClass: Class<*>,
        versionId: String,
        heapMb: Int,
        runtimeName: String,
        playerName: String
    ) {
        val safePlayerName = sanitizePlayerName(playerName)
        val launcherProfilesClass = Class.forName(LAUNCHER_PROFILES_CLASS)
        launcherProfilesClass.getMethod("load").invoke(null)

        val profileClass = Class.forName(MINECRAFT_PROFILE_CLASS)
        val gameProfile = profileClass.getDeclaredConstructor().newInstance()
        setPublicField(gameProfile, "name", "TSI $versionId")
        setPublicField(gameProfile, "lastVersionId", versionId)
        setPublicField(
            gameProfile,
            "gameDir",
            ".minecraft/instances/$versionId/game"
        )
        val runtimePrefix = toolsClass.getField("LAUNCHERPROFILES_RTPREFIX").get(null) as String
        setPublicField(gameProfile, "javaDir", runtimePrefix + runtimeName)

        val mainProfiles = launcherProfilesClass.getField("mainProfileJson").get(null)
            ?: throw IllegalStateException("Amethyst launcher profiles were not initialized.")
        @Suppress("UNCHECKED_CAST")
        val profileMap = mainProfiles.javaClass.getField("profiles").get(mainProfiles)
            as MutableMap<String, Any>

        val profileKey = UUID.nameUUIDFromBytes(
            "tsi-amethyst-profile:$versionId".toByteArray(Charsets.UTF_8)
        ).toString()
        profileMap[profileKey] = gameProfile
        launcherProfilesClass.getMethod("write").invoke(null)

        val preferencesClass = Class.forName(LAUNCHER_PREFERENCES_CLASS)
        val preferences = preferencesClass.getField("DEFAULT_PREF").get(null) as? SharedPreferences
            ?: throw IllegalStateException("Amethyst preferences are not ready.")
        val selectedProfileKey =
            preferencesClass.getField("PREF_KEY_CURRENT_PROFILE").get(null) as String
        preferences.edit()
            .putString(selectedProfileKey, profileKey)
            .putString("defaultRuntime", runtimeName)
            .putInt("allocation", heapMb)
            .apply()

        val accountDirectory = File(
            toolsClass.getField("DIR_ACCOUNT_NEW").get(null) as String
        )
        if (!accountDirectory.exists() && !accountDirectory.mkdirs()) {
            throw IllegalStateException("Cannot create Amethyst local account folder.")
        }

        val accountClass = Class.forName(MINECRAFT_ACCOUNT_CLASS)
        val account = accountClass.getDeclaredConstructor().newInstance()
        setPublicField(account, "username", safePlayerName)
        setPublicField(account, "accessToken", "0")
        setPublicField(account, "clientToken", UUID.randomUUID().toString())
        setPublicField(account, "profileId", UUID.randomUUID().toString().replace("-", ""))
        setPublicField(account, "selectedVersion", versionId)
        accountClass.getMethod("save").invoke(account)

        val pojavProfileClass = Class.forName(POJAV_PROFILE_CLASS)
        pojavProfileClass.getMethod(
            "setCurrentProfile",
            Context::class.java,
            Any::class.java
        ).invoke(null, context, safePlayerName)
    }

    private fun setPublicField(instance: Any, name: String, value: Any) {
        instance.javaClass.getField(name).set(instance, value)
    }

    private fun sanitizePlayerName(value: String): String {
        val candidate = value
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(16)
            .ifBlank { "TSIPlayer" }
        return candidate
    }

    private fun safeSegment(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") {
            "Invalid Minecraft version ID"
        }
        return safe
    }
}
