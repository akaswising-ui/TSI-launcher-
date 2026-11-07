package com.tsi.launcher.runtime

import android.app.ActivityManager
import android.content.Context

/**
 * Conservative Minecraft heap budget based on physical Android RAM.
 *
 * Android reports totalMem without marketing "extended RAM" additions. Keep
 * enough memory for the launcher, Android services, graphics buffers and the
 * embedded JVM/native engine instead of assigning almost all device RAM to
 * the Java heap.
 */
object DevicePerformanceProfile {
    fun totalPhysicalRamMb(context: Context): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 4096
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1024)
    }

    fun recommendedHeapMb(context: Context): Int {
        val totalMb = totalPhysicalRamMb(context)
        return when {
            totalMb >= 7680 -> 2048
            totalMb >= 4608 -> 1536
            else -> 1024
        }
    }

    fun maxHeapMb(context: Context): Int {
        val totalMb = totalPhysicalRamMb(context)
        return when {
            totalMb >= 7680 -> 3072
            totalMb >= 4608 -> 2048
            else -> 1536
        }
    }

    fun clampHeap(context: Context, requestedMb: Int): Int {
        return requestedMb.coerceIn(512, maxHeapMb(context))
    }
}
