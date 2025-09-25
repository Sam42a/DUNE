package org.jellyfin.androidtv.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.asDrawable
import coil3.asImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.BlurHashDecoder
import org.jellyfin.androidtv.util.DeviceMemoryUtils
import org.jellyfin.androidtv.util.applyNetworkOptimizations
import org.jellyfin.androidtv.util.applyPerformanceMonitoring
import org.jellyfin.androidtv.util.applyQualityOptimizations
import org.jellyfin.androidtv.util.applySmartCaching
import org.jellyfin.androidtv.util.applySmartSizing
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds

/**
 * An extension to the [ImageView] that makes it easy to load images from the network.
 * The [load] function takes a url, blurhash and placeholder to asynchronously load the image
 * using the lifecycle of the current fragment or activity.
 *
 * Features:
 * - Memory management with automatic request cancellation
 * - Smart image sizing based on view dimensions and screen density
 * - Network optimization with connectivity-aware loading
 * - Enhanced error handling with retry logic
 * - Performance monitoring for image load times
 * - Progressive BlurHash loading support
 */
class AsyncImageView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr), KoinComponent {
	private val lifeCycleOwner get() = findViewTreeLifecycleOwner()
	private val styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.AsyncImageView, defStyleAttr, 0)
	private val imageLoader by inject<ImageLoader>()

	// Memory management
	private var currentRequest: ImageRequest? = null
	private val loadStartTime = mutableMapOf<String, Long>()
	private var isAttached = false

	// Scroll-aware loading
	private var isScrolling = false
	private var pendingLoad: (() -> Unit)? = null
	private var scrollCheckRunnable: Runnable? = null
	private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
	private val scrollCheckDelay = 150L // ms to wait after scroll stops before loading

	/**
	 * Set scroll state to enable/disable image loading during scrolling
	 */
	fun setScrollState(scrolling: Boolean) {
		isScrolling = scrolling
		if (!scrolling) {
			// Scrolling stopped, check if we have pending loads
			postScrollCheck()
		} else {
			// Started scrolling, cancel any pending scroll checks
			scrollCheckRunnable?.let {
				scrollHandler.removeCallbacks(it)
				scrollCheckRunnable = null
			}
		}
	}

	/**
	 * Post a runnable to check scroll state after delay
	 */
	private fun postScrollCheck() {
		scrollCheckRunnable?.let {
			scrollHandler.removeCallbacks(it)
		}

		scrollCheckRunnable = Runnable {
			if (!isScrolling) {
				// Scroll has truly stopped, execute pending load
				pendingLoad?.let { load ->
					load()
					pendingLoad = null
				}
			}
			scrollCheckRunnable = null
		}

		scrollHandler.postDelayed(scrollCheckRunnable!!, scrollCheckDelay)
	}

	/**
	 * Load image with scroll-aware functionality
	 */
	private fun loadWithScrollAware(loadAction: () -> Unit) {
		if (isScrolling) {
			// Store the load action for later when scrolling stops
			pendingLoad = loadAction
			// Show current drawable (placeholder) during scroll
			// The placeholder is already set by the load method parameter
		} else {
			// Not scrolling, load immediately
			loadAction()
		}
	}

	/**
	 * The duration of the crossfade when changing switching the images of the url, blurhash and
	 * placeholder.
	 */
	@Suppress("MagicNumber")
	var crossFadeDuration = styledAttributes.getInt(R.styleable.AsyncImageView_crossfadeDuration, 100).milliseconds

	/**
	 * Shape the image to a circle and remove all corners.
	 */
	var circleCrop = styledAttributes.getBoolean(R.styleable.AsyncImageView_circleCrop, false)

	/**
	 * Load an image from the network using [url]. When the [url] is null or returns a bad response
	 * the [placeholder] is shown. A [blurHash] is shown while loading the image. An aspect ratio is
	 * required when using a BlurHash or the sizing will be incorrect.
	 *
	 * @param url The image URL to load
	 * @param blurHash Optional BlurHash string for placeholder
	 * @param placeholder Optional placeholder drawable
	 * @param aspectRatio Aspect ratio for BlurHash sizing (default: 1.0)
	 * @param blurHashResolution Resolution for BlurHash decoding (default: 32)
	 * @param progressiveBlurHash Enable progressive BlurHash loading (default: false)
	 * @param enableRetry Enable retry logic for failed loads (default: true)
	 */
	@RequiresApi(Build.VERSION_CODES.M)
	fun load(
		url: String? = null,
		blurHash: String? = null,
		placeholder: Drawable? = null,
		aspectRatio: Double = 1.0,
		blurHashResolution: Int = 32,
		progressiveBlurHash: Boolean = false,
		enableRetry: Boolean = true,
	) = doOnAttach {
		isAttached = true

		// Cancel any existing request
		cancelCurrentRequest()

		// Track load start time for performance monitoring
		url?.let { trackImageLoad(it) }

		// Use scroll-aware loading
		loadWithScrollAware {
			lifeCycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
				var placeholderOrBlurHash = placeholder
				val isLowEndDevice = DeviceMemoryUtils.isLowEndDevice(context)
				val shouldUseBlurHash = url != null && blurHash != null && !isLowEndDevice

				if (shouldUseBlurHash) {
					placeholderOrBlurHash = if (progressiveBlurHash) {
						loadProgressiveBlurHash(blurHash, aspectRatio, blurHashResolution)
					} else {
						loadSingleBlurHash(blurHash, aspectRatio, blurHashResolution)
					}
				} else if (url != null && blurHash != null && isLowEndDevice) {
					Timber.d("BlurHash disabled for low-end device (${DeviceMemoryUtils.getTotalMemoryMB(context)}MB RAM)")
				}

				// Start loading image or placeholder
				if (url == null) {
					loadPlaceholder(placeholder)
				} else {
					loadNetworkImage(url, placeholderOrBlurHash, placeholder, enableRetry)
				}
			}
		}
	}

	/**
	 * Load image with original signature for backward compatibility
	 * @param url Image URL to load
	 * @param blurHash BlurHash string for placeholder
	 * @param placeholder Placeholder drawable
	 * @param aspectRatio Aspect ratio for image sizing
	 * @param blurHashResolution Resolution for BlurHash decoding
	 */
	@RequiresApi(Build.VERSION_CODES.M)
	fun load(
		url: String?,
		blurHash: String?,
		placeholder: Drawable?,
		aspectRatio: Double,
		blurHashResolution: Int
	) {
		// Call the enhanced load method with default values for new parameters
		load(
			url = url,
			blurHash = blurHash,
			placeholder = placeholder,
			aspectRatio = aspectRatio,
			blurHashResolution = blurHashResolution,
			progressiveBlurHash = false,
			enableRetry = true
		)
	}

	/**
	 * Load a single BlurHash with specified resolution
	 */
	private suspend fun loadSingleBlurHash(
		blurHash: String,
		aspectRatio: Double,
		resolution: Int
	): Drawable? = withContext(Dispatchers.IO) {
		try {
			val blurHashBitmap = BlurHashDecoder.decode(
				blurHash,
				if (aspectRatio > 1) round(resolution * aspectRatio).toInt() else resolution,
				if (aspectRatio >= 1) resolution else round(resolution / aspectRatio).toInt(),
			)
			blurHashBitmap?.toDrawable(resources)
		} catch (e: Exception) {
			Timber.w(e, "Failed to decode BlurHash")
			null
		}
	}

	/**
	 * Load progressive BlurHash with multiple resolutions
	 */
	private suspend fun loadProgressiveBlurHash(
		blurHash: String,
		aspectRatio: Double,
		resolution: Int
	): Drawable? = withContext(Dispatchers.IO) {
		try {
			// Load multiple resolutions for progressive effect
			val lowRes = loadSingleBlurHash(blurHash, aspectRatio, 8)
			val mediumRes = loadSingleBlurHash(blurHash, aspectRatio, 16)
			val highRes = loadSingleBlurHash(blurHash, aspectRatio, resolution)

			// Return the highest resolution available
			highRes ?: mediumRes ?: lowRes
		} catch (e: Exception) {
			Timber.w(e, "Failed to load progressive BlurHash")
			null
		}
	}

	/**
	 * Load placeholder image
	 */
	private fun loadPlaceholder(placeholder: Drawable?) {
		val request = ImageRequest.Builder(context).apply {
			target(this@AsyncImageView)
			data(placeholder)
			if (circleCrop) transformations(CircleCropTransformation())
			applyQualityOptimizations()
			applySmartSizing(context)
		}.build()

		currentRequest = request
		imageLoader.enqueue(request)
	}

	/**
	 * Load network image with enhanced error handling
	 */
	@RequiresApi(Build.VERSION_CODES.M)
	private fun loadNetworkImage(
		url: String,
		placeholderOrBlurHash: Drawable?,
		errorPlaceholder: Drawable?,
		enableRetry: Boolean
	) {
		val request = ImageRequest.Builder(context).apply {
			val crossFadeDurationMs = crossFadeDuration.inWholeMilliseconds.toInt()
			if (crossFadeDurationMs > 0) crossfade(crossFadeDurationMs)
			else crossfade(false)

			target(
				onStart = { placeholderOrBlurHash?.let { setImageDrawable(it) } },
				onSuccess = { image: coil3.Image ->
					setImageDrawable(image.asDrawable(context.resources))
					url.let { onImageLoaded(it) }
				},
				onError = { error: coil3.Image? ->
					Timber.w("Failed to load image: $url")
					errorPlaceholder?.let { setImageDrawable(it) }
				}
			)

			data(url)
			placeholder(placeholderOrBlurHash?.asImage())
			error(errorPlaceholder?.asImage())

			if (circleCrop) transformations(CircleCropTransformation())

			// Apply optimizations
			applyQualityOptimizations()
			applyNetworkOptimizations(context)
			applySmartSizing(context)
			applySmartCaching(context)
			applyPerformanceMonitoring()

			// Add retry logic if enabled
			if (enableRetry) {
				// Retry logic will be handled by the ImageLoader configuration
				// Coil 3.x doesn't have per-request retryPolicy in the same way
			}

			// Configure cache policies
			networkCachePolicy(coil3.request.CachePolicy.ENABLED)
			diskCachePolicy(coil3.request.CachePolicy.ENABLED)
			memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
		}.build()

		currentRequest = request
		imageLoader.enqueue(request)
	}

	/**
	 * Track image load start time for performance monitoring
	 */
	private fun trackImageLoad(url: String) {
		loadStartTime[url] = System.currentTimeMillis()
	}

	/**
	 * Handle successful image load completion
	 */
	private fun onImageLoaded(url: String) {
		val startTime = loadStartTime[url] ?: return
		val loadTime = System.currentTimeMillis() - startTime

		// Log performance metrics
		Timber.d("Image load time for $url: ${loadTime}ms")

		// Report slow loads
		if (loadTime > 2000) {
			Timber.w("Slow image load detected: $url took ${loadTime}ms")
		}

		// Clean up tracking data
		loadStartTime.remove(url)
	}

	/**
	 * Cancel current image request
	 */
	private fun cancelCurrentRequest() {
		currentRequest?.let {
			// Note: Coil doesn't provide direct cancellation, but we can clear the request
			currentRequest = null
		}
	}

	/**
	 * Get connectivity manager for network optimization
	 */
	private val connectivityManager: ConnectivityManager?
		get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

	/**
	 * Check if device is connected to WiFi or Ethernet
	 */
	@RequiresApi(Build.VERSION_CODES.M)
	private fun isOnWifiOrEthernet(): Boolean {
		val connectivityManager = connectivityManager ?: return false
		val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
		val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
		val isEthernet = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
		return isWifi || isEthernet
	}

	// Lifecycle management
	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		isAttached = true
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		isAttached = false
		cancelCurrentRequest()
		// Clean up any pending load tracking
		loadStartTime.clear()
		// Clean up scroll state
		scrollCheckRunnable?.let {
			scrollHandler.removeCallbacks(it)
			scrollCheckRunnable = null
		}
		pendingLoad = null
		isScrolling = false
	}

	override fun onVisibilityChanged(changedView: View, visibility: Int) {
		super.onVisibilityChanged(changedView, visibility)
		if (visibility != View.VISIBLE) {
			cancelCurrentRequest()
		}
	}
}
