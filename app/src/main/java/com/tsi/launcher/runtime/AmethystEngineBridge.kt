package com.tsi.launcher.runtime

import android.content.Context
import java.io.File

/**
 * Capability probe for the Amethyst Android engine.
 * A JRE alone is not proof that JVM startup, LWJGL, SDL, graphics, audio
 * and input are packaged and ready to launch a Minecraft client.
 */
object AmethystEngineBridge {
    private const val LAUNCHER_ACTIVITY = "net.kdt.pojavlaunch.MainActivity"
    private val requiredNativeLibraries = listOf(
        "libpojavexec.so",
        "libgl4es_114.so",
        "libOSMesa_8.so"
    )

    fun isAvailable(context: Context): Boolean {
        val launcherPresent = runCatching {
            Class.forName(LAUNCHER_ACTIVITY, false, context.classLoader)
        }.isSuccess
        if (!launcherPresent) return false

        val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
        return requiredNativeLibraries.any { File(nativeDirectory, it).isFile }
    }

    fun missingComponents(context: Context): List<String> {
        val missing = mutableListOf<String>()
        val launcherPresent = runCatching {
            Class.forName(LAUNCHER_ACTIVITY, false, context.classLoader)
        }.isSuccess
        if (!launcherPresent) {
            missing += "Amethyst Android launcher host / JVM startup bridge"
        }
        val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
        if (requiredNativeLibraries.none { File(nativeDirectory, it).isFile }) {
            missing += "ABI-matched LWJGL, SDL and graphics native libraries"
        }
        missing += "TSI game launch adapter and surface/audio/input lifecycle"
        return missing
    }
}
