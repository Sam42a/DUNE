package org.jellyfin.androidtv.data.service

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackgroundService(
	private val context: Context,
	private val jellyfin: Jellyfin,
	private val api: ApiClient,
	private val userPreferences: UserPreferences,
	private val imageLoader: ImageLoader,
	private val imageHelper: ImageHelper,
) {
	companion object {
		val SLIDESHOW_DURATION = 30.seconds
		val TRANSITION_DURATION = 800.milliseconds
	}

	// Async
	private val scope = MainScope()
	private var loadBackgroundsJob: Job? = null
	private var updateBackgroundTimerJob: Job? = null
	private var lastBackgroundTimerUpdate = 0L

	// Current background data
	private var _backgrounds = emptyList<ImageBitmap>()
	private var _currentIndex = 0
	private var _currentBackground = MutableStateFlow<ImageBitmap?>(null)
	private var _enabled = MutableStateFlow(true)
	private var _preventLoginBackgroundOverride = MutableStateFlow(false)
	val currentBackground get() = _currentBackground.asStateFlow()
	val enabled get() = _enabled.asStateFlow()
	private var _dimmingIntensity = MutableStateFlow(0.5f)
	val backdropDimmingIntensity get() = _dimmingIntensity.asStateFlow()
	private var _fadingIntensity = MutableStateFlow(0.7f)
	val backdropFadingIntensity get() = _fadingIntensity.asStateFlow()

	/**
	 * Use splashscreen from [server] as background.
	 */
	fun setBackground(server: Server) {
		// Check if item is set and backgrounds are enabled
		if (!userPreferences[UserPreferences.backdropEnabled] || _preventLoginBackgroundOverride.value)
			return clearBackgrounds()

		// Check if splashscreen is enabled in (cached) branding options
		if (!server.splashscreenEnabled)
			return clearBackgrounds()

		// Reset dimming and fading for login screen
		_dimmingIntensity.value = 0f
		_fadingIntensity.value = 0f

		// Manually grab the backdrop URL
		val api = jellyfin.createApi(baseUrl = server.address)
		val splashscreenUrl = api.imageApi.getSplashscreenUrl()

		loadBackgrounds(setOf(splashscreenUrl))
	}


	/**
	 * Use all available backdrops from [baseItem] as background.
	 * For Media Folders, use primary image as backdrop if no backdrops are available.
	 */
	fun setBackground(baseItem: BaseItemDto?) {
		// Check if item is set and backgrounds are enabled
		if (baseItem == null || !userPreferences[UserPreferences.backdropEnabled])
			return clearBackgrounds()

		// Set dimming and fading intensity
		_dimmingIntensity.value = userPreferences[UserPreferences.backdropDimmingIntensity]
		_fadingIntensity.value = userPreferences[UserPreferences.backdropFadingIntensity]

		// Get all backdrop URLs
		val backdropUrls = (baseItem.itemBackdropImages + baseItem.parentBackdropImages)
			.map { it.getUrl(api) }
			.toMutableSet()

		// If no backdrops are available, use the primary image as fallback
		if (backdropUrls.isEmpty()) {
			val primaryImageUrl = imageHelper.getPrimaryImageUrl(
				item = baseItem,
				width = 1920,
				height = 1080
			)

			if (primaryImageUrl != null) {
				backdropUrls.add(primaryImageUrl)
			}
		}

		loadBackgrounds(backdropUrls)
	}

	private fun loadBackgrounds(backdropUrls: Set<String>) {
		if (backdropUrls.isEmpty()) return clearBackgrounds()

		// Re-enable backgrounds if disabled
		_enabled.value = true

		// Cancel current loading job
		loadBackgroundsJob?.cancel()
		loadBackgroundsJob = scope.launch(Dispatchers.IO) {
			_backgrounds = backdropUrls.mapNotNull { url ->
				try {
					imageLoader.execute(
						request = ImageRequest.Builder(context).data(url).build()
					).image?.toBitmap()?.asImageBitmap()
				} catch (e: Exception) {
					null
				}
			}

			// Go to first background
			_currentIndex = 0
			update()
		}
	}

	fun clearBackgrounds() {
		_preventLoginBackgroundOverride.value = false
		loadBackgroundsJob?.cancel()

		// Re-enable backgrounds if disabled
		_enabled.value = true

		if (_backgrounds.isEmpty()) return

		_backgrounds = emptyList()
		update()
	}

	/**
	 * Disable the showing of backgrounds until any function manipulating the backgrounds is called.
	 */
	fun disable() {
		_enabled.value = false
	}

	/**
	 * Prevent background override for login screens
	 */
	fun preventLoginBackgroundOverride() {
		_preventLoginBackgroundOverride.value = true
	}

	/**
	 * Allow background override for login screens
	 */
	fun allowLoginBackgroundOverride() {
		_preventLoginBackgroundOverride.value = false
	}

	internal fun update() {
		val now = Instant.now().toEpochMilli()
		if (lastBackgroundTimerUpdate > now - TRANSITION_DURATION.inWholeMilliseconds)
			return setTimer((lastBackgroundTimerUpdate - now).milliseconds + TRANSITION_DURATION, false)

		lastBackgroundTimerUpdate = now

		// Get next background to show
		if (_currentIndex >= _backgrounds.size) _currentIndex = 0

		// Set background
		_currentBackground.value = _backgrounds.getOrNull(_currentIndex)

		// Set timer for next background
		if (_backgrounds.size > 1) setTimer()
		else updateBackgroundTimerJob?.cancel()
	}

	private fun setTimer(updateDelay: Duration = SLIDESHOW_DURATION, increaseIndex: Boolean = true) {
		updateBackgroundTimerJob?.cancel()
		updateBackgroundTimerJob = scope.launch {
			delay(updateDelay)

			if (increaseIndex) _currentIndex++

			update()
		}
	}
}
