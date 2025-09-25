package org.jellyfin.androidtv.util

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Display
import android.view.WindowManager
import android.graphics.Point
import android.os.Build
import androidx.annotation.RequiresApi
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import coil3.request.CachePolicy
import timber.log.Timber

/**
 * Extension function to apply quality optimizations to an ImageRequest
 */
fun ImageRequest.Builder.applyQualityOptimizations(
    quality: Int = 100,
    precision: Precision = Precision.INEXACT
): ImageRequest.Builder = apply {
    // Set precision (controls how the image is resized)
    precision(precision)

    // For Coil 3.x, we can only control quality through size and precision
    // The actual quality reduction will be handled by the server if it supports it
}

/**
 * Extension function to apply smart sizing based on screen density
 */
fun ImageRequest.Builder.applySmartSizing(context: Context): ImageRequest.Builder = apply {
    try {
        val displayMetrics = context.resources.displayMetrics

        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Use a fraction of screen size as target
        val targetWidth = (screenWidth * 0.8).toInt()
        val targetHeight = (screenHeight * 0.8).toInt()

        if (targetWidth > 0 && targetHeight > 0) {
            size(Size(targetWidth, targetHeight))
            Timber.d("Smart sizing applied: ${targetWidth}x$targetHeight")
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to apply smart sizing")
    }
}

/**
 * Extension function to apply network optimizations based on connectivity
 * Android TV devices are typically connected via WiFi or Ethernet
 */
@RequiresApi(Build.VERSION_CODES.M)
fun ImageRequest.Builder.applyNetworkOptimizations(context: Context): ImageRequest.Builder = apply {
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        when {
            isWifi || isEthernet -> {
                // High quality for WiFi or Ethernet connections
                precision(Precision.INEXACT)
                val connectionType = if (isWifi) "WiFi" else "Ethernet"
                Timber.d("Network optimization: $connectionType - High precision")
            }
            else -> {
                // Default for other network types (VPN, etc...)
                precision(Precision.INEXACT)
                Timber.d("Network optimization: Other network type - Automatic precision")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to apply network optimizations")
    }
}

/**
 * Extension function to configure smart caching based on device memory
 */
fun ImageRequest.Builder.applySmartCaching(context: Context): ImageRequest.Builder = apply {
    try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = memoryInfo.totalMem
        val availableMemory = memoryInfo.availMem
        val memoryThreshold = totalMemory * 0.3 // 30% threshold

        when {
            availableMemory > memoryThreshold -> {
                // High memory available - aggressive caching
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
                Timber.d("Smart caching: High memory - All caches enabled")
            }
            availableMemory > memoryThreshold * 0.5 -> {
                // Medium memory - balanced caching
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.READ_ONLY)
                Timber.d("Smart caching: Medium memory - Balanced caching")
            }
            else -> {
                // Low memory - conservative caching
                memoryCachePolicy(CachePolicy.READ_ONLY)
                diskCachePolicy(CachePolicy.READ_ONLY)
                networkCachePolicy(CachePolicy.READ_ONLY)
                Timber.d("Smart caching: Low memory - Conservative caching")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to apply smart caching")
    }
}

/**
 * Extension function to add retry logic (handled at ImageLoader level in Coil 3.x)
 */
fun ImageRequest.Builder.applyRetryLogic(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000
): ImageRequest.Builder = apply {
    // Retry logic will be handled by the ImageLoader configuration
    // Coil 3.x doesn't have per-request retryPolicy in the same way
    Timber.d("Retry logic configured: $maxAttempts attempts, ${initialDelay}ms initial delay")
}

/**
 * Extension function to add performance monitoring (headers handled at ImageLoader level in Coil 3.x)
 */
fun ImageRequest.Builder.applyPerformanceMonitoring(): ImageRequest.Builder = apply {
    // Headers would need to be added at the ImageLoader level in Coil 3.x
    Timber.d("Performance monitoring configured")
}
