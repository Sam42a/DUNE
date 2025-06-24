package org.jellyfin.androidtv.util

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles preloading of images for better performance.
 */
object ImagePreloader : KoinComponent {
    private val imageLoader by inject<ImageLoader>()
    private val userPreferences by inject<UserPreferences>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Preload a list of image URLs.
     * @param context The context to use for the image requests
     * @param urls List of image URLs to preload
     */
    fun preloadImages(
        context: Context,
        urls: List<String?>
    ) {
        if (!userPreferences[UserPreferences.preloadImages]) return

        urls.forEach { url ->
            if (!url.isNullOrBlank()) {
                scope.launch {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .size(Size.ORIGINAL)
                            .build()
                        imageLoader.enqueue(request)
                    } catch (e: Exception) {
                        // Ignore individual image loading failures
                    }
                }
            }
        }
    }

    /**
     * Preload images for a specific screen or component.
     * @param context The context to use for the image requests
     * @param imageUrls Map of image URLs to preload, where the key is a unique identifier
     */
    fun preloadScreenImages(
        context: Context,
        imageUrls: Map<String, String?>
    ) {
        preloadImages(context, imageUrls.values.toList())
    }

    /**
     * Cancel all pending preload requests.
     */
    fun cancelAllPreloads() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }
}
