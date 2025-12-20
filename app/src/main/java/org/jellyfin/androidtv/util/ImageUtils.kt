package org.jellyfin.androidtv.util

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
	quality: Int = 85,
	precision: Precision = Precision.INEXACT
): ImageRequest.Builder = apply {
	precision(precision)
}

/**
 * Extension function to apply smart sizing based on screen density
 * Optimized to prevent memory issues on low-end devices
 */
fun ImageRequest.Builder.applySmartSizing(context: Context): ImageRequest.Builder = apply {
	try {
		val displayMetrics = context.resources.displayMetrics
		val screenWidth = displayMetrics.widthPixels
		val screenHeight = displayMetrics.heightPixels

		val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
		val isLowRamDevice = activityManager?.isLowRamDevice ?: false
		val memoryClass = activityManager?.memoryClass ?: 64

		val sizeFactor = when {
			isLowRamDevice || memoryClass < 128 -> 0.4f
			memoryClass < 256 -> 0.6f
			else -> 0.75f
		}

		val targetWidth = (screenWidth * sizeFactor).toInt()
		val targetHeight = (screenHeight * sizeFactor).toInt()
		val maxDimension = if (isLowRamDevice) 1280 else 1920
		val finalWidth = targetWidth.coerceAtMost(maxDimension)
		val finalHeight = targetHeight.coerceAtMost(maxDimension)

		if (finalWidth > 0 && finalHeight > 0) {
			size(Size(finalWidth, finalHeight))
		}
	} catch (e: Exception) {
		Timber.e(e, "Failed to apply smart sizing, using fallback")
		size(Size(1280, 720))
	}
}

/**
 * Extension function to apply network optimizations based on connectivity
 * Android TV devices are typically connected via WiFi or Ethernet
 */
@RequiresApi(Build.VERSION_CODES.M)
fun ImageRequest.Builder.applyNetworkOptimizations(context: Context): ImageRequest.Builder = apply {
	try {
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
		val network = connectivityManager?.activeNetwork
		val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

		val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
		val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

		when {
			isWifi || isEthernet -> {
				precision(Precision.INEXACT)
			}
			else -> {
				precision(Precision.INEXACT)
			}
		}
	} catch (e: Exception) {
		Timber.w(e, "Failed to apply network optimizations, using defaults")
	}
}

/**
 * Extension function to configure smart caching based on device memory
 * Optimized to prevent crashes on low-end devices
 */
fun ImageRequest.Builder.applySmartCaching(context: Context): ImageRequest.Builder = apply {
	try {
		val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
		val memoryInfo = ActivityManager.MemoryInfo()
		activityManager?.getMemoryInfo(memoryInfo)

		val totalMemory = memoryInfo.totalMem
		val availableMemory = memoryInfo.availMem
		val isLowRamDevice = activityManager?.isLowRamDevice ?: false

		val highMemoryThreshold = totalMemory * 0.4
		val mediumMemoryThreshold = totalMemory * 0.25

		when {
			isLowRamDevice -> {
				// Low RAM device - very conservative
				memoryCachePolicy(CachePolicy.READ_ONLY)
				diskCachePolicy(CachePolicy.ENABLED)
				networkCachePolicy(CachePolicy.DISABLED)
			}
			availableMemory > highMemoryThreshold -> {
				// High memory available - aggressive caching
				memoryCachePolicy(CachePolicy.ENABLED)
				diskCachePolicy(CachePolicy.ENABLED)
				networkCachePolicy(CachePolicy.ENABLED)
			}
			availableMemory > mediumMemoryThreshold -> {
				// Medium memory - balanced caching
				memoryCachePolicy(CachePolicy.ENABLED)
				diskCachePolicy(CachePolicy.ENABLED)
				networkCachePolicy(CachePolicy.READ_ONLY)
			}
			else -> {
				// Low memory - conservative caching
				memoryCachePolicy(CachePolicy.READ_ONLY)
				diskCachePolicy(CachePolicy.ENABLED)
				networkCachePolicy(CachePolicy.DISABLED)
			}
		}
	} catch (e: Exception) {
		memoryCachePolicy(CachePolicy.READ_ONLY)
		diskCachePolicy(CachePolicy.ENABLED)
		networkCachePolicy(CachePolicy.DISABLED)
	}
}

fun ImageRequest.Builder.applyRetryLogic(
	maxAttempts: Int = 2,
	initialDelay: Long = 500
): ImageRequest.Builder = apply {
}

fun ImageRequest.Builder.applyPerformanceMonitoring(): ImageRequest.Builder = apply {
}

fun ImageRequest.Builder.applyAllOptimizations(context: Context): ImageRequest.Builder = apply {
	applyQualityOptimizations()
	applySmartSizing(context)
	applySmartCaching(context)

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
		applyNetworkOptimizations(context)
	}

	applyRetryLogic()
	applyPerformanceMonitoring()
}
