package com.tsi.launcher.bedrock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class BedrockInstallStatus(
    val installed: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val message: String
)

object BedrockIntegration {
    const val OFFICIAL_PACKAGE = "com.mojang.minecraftpe"

    fun readInstallStatus(context: Context): BedrockInstallStatus {
        return try {
            val info = getPackageInfo(context.packageManager)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

            BedrockInstallStatus(
                installed = true,
                versionName = info.versionName ?: "Unknown",
                versionCode = versionCode,
                message = "Official Minecraft for Android is installed"
            )
        } catch (_: PackageManager.NameNotFoundException) {
            BedrockInstallStatus(
                installed = false,
                versionName = null,
                versionCode = null,
                message = "Official Minecraft for Android was not found"
            )
        }
    }

    fun launchInstalledGame(context: Context) {
        val intent: Intent = context.packageManager
            .getLaunchIntentForPackage(OFFICIAL_PACKAGE)
            ?: throw IllegalStateException("Minecraft Bedrock has no launchable activity")

        context.startActivity(intent)
    }

    private fun getPackageInfo(packageManager: PackageManager): PackageInfo {
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                OFFICIAL_PACKAGE,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(OFFICIAL_PACKAGE, 0)
        }
    }
}
