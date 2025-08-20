package org.jellyfin.androidtv.util

import android.content.Context
import android.content.pm.PackageManager

object DeviceUtils {
    /**
     * Check if the current device is an Amazon Fire TV device
     */
    fun isFireTv(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }

    /**
     * Check if the current device is an Android TV device
     */
    fun isAndroidTv(context: Context): Boolean {
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
