package com.tsi.launcher.bedrock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android has one official Bedrock package for Stable and Beta.
 * Joining Beta updates this same package; it does not install a second client.
 */
data class BedrockInstallStatus(
    val installed: Boolean,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val firstInstallTime: Long?,
    val lastUpdateTime: Long?,
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
                packageName = info.packageName,
                versionName = info.versionName ?: "Unknown",
                versionCode = versionCode,
                firstInstallTime = info.firstInstallTime,
                lastUpdateTime = info.lastUpdateTime,
                message = "Official Minecraft for Android is installed"
            )
        } catch (_: PackageManager.NameNotFoundException) {
            BedrockInstallStatus(
                installed = false,
                packageName = OFFICIAL_PACKAGE,
                versionName = null,
                versionCode = null,
                firstInstallTime = null,
                lastUpdateTime = null,
                message = "Official Minecraft for Android was not found"
            )
        } catch (error: Exception) {
            BedrockInstallStatus(
                installed = false,
                packageName = OFFICIAL_PACKAGE,
                versionName = null,
                versionCode = null,
                firstInstallTime = null,
                lastUpdateTime = null,
                message = "Could not inspect Minecraft installation: ${error.message ?: "unknown error"}"
            )
        }
    }

    /**
     * Stable and Beta both launch the same official Android package.
     * The active Play testing track is managed by Google Play, not exposed
     * through PackageManager.
     */
    fun launchInstalledGame(context: Context) {
        val intent: Intent = context.packageManager
            .getLaunchIntentForPackage(OFFICIAL_PACKAGE)
            ?: throw IllegalStateException("Minecraft Bedrock has no launchable activity")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openOfficialStorePage(context: Context) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    "https://play.google.com/store/apps/details?id=$OFFICIAL_PACKAGE"
                )
            )
        )
    }

    fun openBetaEnrollment(context: Context) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    "https://play.google.com/apps/testing/$OFFICIAL_PACKAGE"
                )
            )
        )
    }

    fun openAndroidPreviewInformation(context: Context) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    "https://help.minecraft.net/hc/en-us/articles/4423653831821"
                )
            )
        )
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
