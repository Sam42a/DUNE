package org.jellyfin.androidtv.ui.playback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.util.sdk.getDisplayName
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Data class to hold subtitle information
 */
private data class SubtitleInfo(
    val url: String,
    val name: String,
    val language: String,
    val mimeType: String,
    val isDefault: Boolean = false,
    val isForced: Boolean = false
)

/**
 * Activity that, once opened, opens the first item of the [VideoQueueManager.getCurrentVideoQueue] list in an external media player app.
 * Once returned it will notify the server of item completion.
 */
class ExternalPlayerActivity : FragmentActivity() {
	companion object {
		const val EXTRA_POSITION = "position"

		// Minimum percentage of the video that needs to be watched to be marked as completed
		private const val MINIMUM_COMPLETION_PERCENTAGE = 0.9

		// Minimum duration (in seconds) that needs to be watched to update the resume position
		private const val MINIMUM_WATCH_DURATION_SECONDS = 10L

		// https://mx.j2inter.com/api
		private const val API_MX_TITLE = "title"
		private const val API_MX_SEEK_POSITION = "position"
		private const val API_MX_FILENAME = "filename"
		private const val API_MX_SECURE_URI = "secure_uri"
		private const val API_MX_RETURN_RESULT = "return_result"
		private const val API_MX_RESULT_POSITION = "position"
		private const val API_MX_SUBS = "subs"
		private const val API_MX_SUBS_NAME = "subs.name"
		private const val API_MX_SUBS_FILENAME = "subs.filename"

		// https://wiki.videolan.org/Android_Player_Intents/
		private const val API_VLC_SUBTITLES = "subtitles_location"
		private const val API_VLC_RESULT_POSITION = "extra_position"

		// https://www.vimu.tv/player-api
		private const val API_VIMU_TITLE = "forcename"
		private const val API_VIMU_SEEK_POSITION = "startfrom"
		private const val API_VIMU_RESUME = "forceresume"

		// The extra keys used by various video players to read the end position
		private val resultPositionExtras = arrayOf(API_MX_RESULT_POSITION, API_VLC_RESULT_POSITION)
	}

	private val videoQueueManager by inject<VideoQueueManager>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val api by inject<ApiClient>()

	private val playVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		Timber.i("Playback finished with result code ${result.resultCode}")

		// Only show an error if the result indicates a failure (like RESULT_CANCELED with specific error data)
		val isError = result.resultCode == RESULT_CANCELED && result.data?.extras?.keySet()?.isNotEmpty() == true

		if (isError) {
			Timber.w("External player reported an error: ${result.data}")
			Toast.makeText(this, R.string.video_error_unknown_error, Toast.LENGTH_LONG).show()
		}

		// Always update the queue position and process the result
		videoQueueManager.setCurrentMediaPosition(videoQueueManager.getCurrentMediaPosition() + 1)
		onItemFinished(result.data)
	}

	private var currentItem: Pair<BaseItemDto, MediaSourceInfo>? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val position = intent.getLongExtra(EXTRA_POSITION, 0).milliseconds
		playNext(position)
	}

	private fun playNext(position: Duration = Duration.ZERO) {
		val currentPosition = videoQueueManager.getCurrentMediaPosition()
		val item = videoQueueManager.getCurrentVideoQueue().getOrNull(currentPosition) ?: return finish()
		val mediaSource = item.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == item.id }

		if (mediaSource == null) {
			Toast.makeText(this, R.string.msg_no_playable_items, Toast.LENGTH_LONG).show()
			finish()
		} else {
			playItem(item, mediaSource, position)
		}
	}

	private fun playItem(item: BaseItemDto, mediaSource: MediaSourceInfo, position: Duration) {
		val url = api.videosApi.getVideoStreamUrl(
			itemId = item.id,
			mediaSourceId = mediaSource.id,
			static = true,
		)

		val title = item.getDisplayName(this)
		val fileName = mediaSource.path?.let { File(it).name }
		// Get all media streams for debugging
		val allStreams = mediaSource.mediaStreams.orEmpty()
		Timber.d("All media streams (${allStreams.size}): ${allStreams.joinToString(", ") { "${it.type}(${it.index}):${it.language ?: '?'}${if (it.isExternal) "(ext)" else ""}" }}")

		// Get all subtitle streams, both external and embedded
		val allSubtitles = allStreams
			.filter { it.type == MediaStreamType.SUBTITLE }
			.sortedWith(compareBy<MediaStream> { it.isDefault }.thenBy { it.index })

		Timber.d("Found ${allSubtitles.size} subtitles: ${allSubtitles.joinToString(", ") {
			val type = if (it.isExternal) "External" else "Embedded"
			"${it.displayTitle ?: it.title ?: "Untitled"} ($type, ${it.language ?: '?'}):${it.codec}"
		}}")

		// Prepare subtitle information
		val subtitles = allSubtitles.mapNotNull { subtitle ->
			try {
				val url = if (subtitle.isExternal) {
					// For external subtitles, get the direct URL with proper parameters
					val baseUrl = api.baseUrl?.trimEnd('/') ?: run {
						Timber.e("Base URL is null, cannot create subtitle URL")
						return@mapNotNull null
					}
					val codec = subtitle.codec?.lowercase() ?: "srt" // Default to srt if codec is null
					val streamUrl = "$baseUrl/Videos/${item.id}/${mediaSource.id}/Subtitles/${subtitle.index}/Stream.$codec"
					val params = listOf(
						"api_key=${api.accessToken}",
						"copyTimestamps=false",
						"addVttTimeMap=false",
						"startPositionTicks=0",
						"mediaSourceId=${mediaSource.id}",
						"itemId=${item.id}"
					).joinToString("&")
					"$streamUrl?$params"
				} else {
					// For embedded subtitles, include the stream index in the video URL
					val videoUrl = api.videosApi.getVideoStreamUrl(
						itemId = item.id,
						mediaSourceId = mediaSource.id,
						static = true,
					).trimEnd('&', '?')
					val separator = if ('?' in videoUrl) '&' else '?'
					"$videoUrl${separator}SubtitleStreamIndex=${subtitle.index}"
				}

				// Determine MIME type based on codec
				val mimeType = when (subtitle.codec?.lowercase()) {
					"subrip", "srt" -> "application/x-subrip"
					"ass", "ssa" -> "text/x-ssa"
					"vtt" -> "text/vtt"
					"pgs" -> "application/pgs"
					"dvdsub", "sub" -> "application/x-sub"
					else -> "text/plain"
				}

				SubtitleInfo(
					url = url,
					name = (subtitle.displayTitle ?: subtitle.title ?: "Subtitle ${subtitle.index}") +
						if (subtitle.isExternal) " (External)" else " (Embedded)",
					language = subtitle.language ?: "",
					mimeType = mimeType,
					isDefault = subtitle.isDefault,
					isForced = subtitle.isForced
				)
			} catch (e: Exception) {
				Timber.e(e, "Error processing subtitle ${subtitle.index}")
				null
			}
		}

		// Log all subtitles
		Timber.i("Found ${subtitles.size} subtitles for item ${item.id}:")
		subtitles.forEachIndexed { index, sub ->
			Timber.i("  Subtitle $index: ${sub.name} (${sub.language.ifEmpty { "??" }}) [${sub.mimeType}] -> ${sub.url}")
		}

		// Extract arrays for legacy support
		val subtitleUrls = subtitles.map { it.url }.toTypedArray()
		val subtitleNames = subtitles.map { it.name }.toTypedArray()
		val subtitleLanguages = subtitles.map { it.language }.toTypedArray()
		val subtitleMimeTypes = subtitles.map { it.mimeType }.toTypedArray()

		val playIntent = Intent(Intent.ACTION_VIEW).apply {
			// Set media type
			val mediaType = when (item.mediaType) {
				MediaType.VIDEO -> "video/*"
				MediaType.AUDIO -> "audio/*"
				else -> "*/*"
			}

			setDataAndTypeAndNormalize(url.toUri(), mediaType)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

			// Common extras for all players
			putExtra("title", title)
			putExtra("name", title)
			putExtra("filename", fileName)
			putExtra("return_result", true)

			// Position in milliseconds
			val positionMs = position.inWholeMilliseconds.toInt()
			putExtra("position", positionMs)

			// MX Player extras
			putExtra(API_MX_SEEK_POSITION, positionMs)
			putExtra(API_MX_RETURN_RESULT, true)
			putExtra(API_MX_TITLE, title)
			putExtra(API_MX_FILENAME, fileName)
			putExtra(API_MX_SECURE_URI, true)

			// Pass subtitles to MX Player
			if (subtitleUrls.isNotEmpty()) {
				putExtra(API_MX_SUBS, subtitleUrls)
				putExtra(API_MX_SUBS_NAME, subtitleNames)
				putExtra(API_MX_SUBS_FILENAME, subtitleLanguages)

				// MX Player Pro supports MIME types
				putExtra("subs.mime", subtitleMimeTypes)
			}

			// VLC extras - multiple formats for better compatibility
			if (subtitleUrls.isNotEmpty()) {
				// Format 1: As separate arrays (most compatible)
				putExtra("subtitles", subtitleUrls)
				putExtra("subtitles.name", subtitleNames)
				putExtra("subtitles.language", subtitleLanguages)
				putExtra("subtitles.mime", subtitleMimeTypes)

				// Format 2: As command line arguments (for VLC)
				val vlcArgs = ArrayList<String>()
				subtitles.forEachIndexed { index, sub ->
					vlcArgs.add("--sub-file=${sub.url}")
					if (sub.language.isNotEmpty()) {
						vlcArgs.add("--sub-language=${sub.language}")
					}
					if (sub.isDefault) {
						vlcArgs.add("--sub-track=$index")
					}
				}
				putExtra("command_args", vlcArgs.toTypedArray())

				// Format 3: As a single subtitle (for backward compatibility)
				putExtra(API_VLC_SUBTITLES, subtitleUrls.first())

				// Format 4: As a list of URIs (for some players)
				val subtitleUris = subtitleUrls.map { android.net.Uri.parse(it) }.toTypedArray()
				putExtra("subs", subtitleUris)
				putExtra("subs.enable", BooleanArray(subtitleUris.size) { true })
			}

			// Just Player extras
			if (subtitleUrls.isNotEmpty()) {
				putExtra("subs_uri", subtitleUrls)
				putExtra("subs_name", subtitleNames)
				putExtra("subs_mime", subtitleMimeTypes)
				putExtra("subs_enable", BooleanArray(subtitleUrls.size) { true })
			}

			// Vimu extras
			putExtra(API_VIMU_SEEK_POSITION, positionMs)
			putExtra(API_VIMU_RESUME, false)
			putExtra(API_VIMU_TITLE, title)

			// Add additional info for debugging
			putExtra("subtitle_count", subtitleUrls.size)

			// Log the intent extras for debugging
			extras?.let { bundle ->
				bundle.keySet().forEach { key ->
					val value = bundle.get(key)
					when (value) {
						is Array<*> -> Timber.d("Intent extra: $key = [${value.joinToString()}]")
						else -> Timber.d("Intent extra: $key = $value")
					}
				}
			}
		}

		try {
			currentItem = item to mediaSource
			playVideoLauncher.launch(playIntent)
		} catch (_: ActivityNotFoundException) {
			Toast.makeText(this, R.string.no_player_message, Toast.LENGTH_LONG).show()
			finish()
		}
	}

    private fun onItemFinished(result: Intent?) {
        if (currentItem == null) {
            Timber.w("No current item when finishing playback")
            // Don't show an error here as it might be a normal exit
            finish()
            return
        }

        val (item, mediaSource) = currentItem!!
        val extras = result?.extras ?: Bundle.EMPTY

        // Get the final position from the external player
        val endPosition = Companion.resultPositionExtras.firstNotNullOfOrNull { key ->
            @Suppress("DEPRECATION") val value = extras.get(key)
            if (value is Number) value.toLong().milliseconds
            else null
        }

        val runtime = (mediaSource.runTimeTicks ?: item.runTimeTicks)?.ticks

        // Only mark as watched if a significant portion was played
        val shouldMarkAsWatched = runtime?.let {
            endPosition != null && endPosition >= (it * MINIMUM_COMPLETION_PERCENTAGE)
        } ?: false

        // Only update the resume position if enough time was watched
        val shouldUpdateResumePosition = runtime?.let {
            endPosition != null &&
            endPosition.inWholeSeconds >= MINIMUM_WATCH_DURATION_SECONDS &&
            endPosition < (it * 0.9) // Don't update if we're close to the end
        } ?: false

        Timber.d("Playback finished - Runtime: ${runtime?.inWholeSeconds}s, Position: ${endPosition?.inWholeSeconds}s, " +
                "Mark as watched: $shouldMarkAsWatched, Update resume: $shouldUpdateResumePosition")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Only report stop if we have a valid position or we're marking as watched
                    if (shouldMarkAsWatched || shouldUpdateResumePosition) {
                        // Report the playback stopped with the final position
                        api.playStateApi.reportPlaybackStopped(
                            PlaybackStopInfo(
                                itemId = item.id,
                                mediaSourceId = mediaSource.id,
                                positionTicks = if (shouldMarkAsWatched) null else endPosition?.inWholeTicks,
                                failed = false,
                            )
                        )

                        // If we're marking as watched, also update the user data
                        if (shouldMarkAsWatched) {
                            Timber.d("Marking item ${item.id} as watched")
                            // The playStateApi call above with positionTicks=null should mark as watched
                            // No need for additional API calls
                        }
                    } else {
                        Timber.d("Not enough watch time to update progress")
                    }
                }
            } catch (error: Exception) {
                Timber.w(error, "Failed to report playback stop event")
                // Don't show an error toast as it might be confusing to the user
            }

            // Update the last playback time
            dataRefreshService.lastPlayback = Instant.now()
            when (item.type) {
                BaseItemKind.MOVIE -> dataRefreshService.lastMoviePlayback = Instant.now()
                BaseItemKind.EPISODE -> dataRefreshService.lastTvPlayback = Instant.now()
                else -> Unit
            }

            // Only auto-play next if we've watched enough of the current item
            if (shouldMarkAsWatched) {
                playNext()
            } else {
                finish()
            }
        }
    }
}
