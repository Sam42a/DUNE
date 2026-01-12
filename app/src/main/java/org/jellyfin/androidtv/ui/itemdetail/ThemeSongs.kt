package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException
import java.util.UUID

class ThemeSongs(private val context: Context) : KoinComponent {

	init {
		instance = this
	}

	private val api by inject<ApiClient>()
	private val sessionRepository by inject<SessionRepository>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val scope = CoroutineScope(Dispatchers.Main)
	private var player: MediaPlayer? = null
	private var activeItemId: UUID? = null
	private var fleeting: Job? = null
	private val archiveHelper by lazy { ArchiveHelper(context) }

	companion object {
		private const val FLEETING_DURATION = 2000L
		private const val FLEETING_STEP = 50L
		private const val BITRATE = 128000
		@Volatile
		private var isAnyFragmentActive = false
		private var instance: ThemeSongs? = null

		@JvmStatic
		fun setAnyFragmentActive(isActive: Boolean) {
			isAnyFragmentActive = isActive
			if (isActive) {
				Timber.d("Fragment became active - AGGRESSIVELY stopping theme songs")
				instance?.let { themeSongs ->
					themeSongs.stop()
					themeSongs.fleeting?.cancel()
					themeSongs.activeItemId = null
					themeSongs.player?.release()
					themeSongs.player = null
				}
			}
		}
	}

	fun isPlaying(): Boolean = player?.isPlaying == true

	fun playThemeSong(item: BaseItemDto, useArchiveFallback: Boolean = true) {
		if (activeItemId == item.id && player?.isPlaying == true) {
			return
		}

		if (isAnyFragmentActive) {
			return
		}

		stop()

		if (!shouldPlayForItem(item)) {
			return
		}

		activeItemId = item.id

		scope.launch(Dispatchers.IO) {
			try {
				val userId = sessionRepository.currentSession.value?.userId
				if (userId != null) {
					val inheritFromParent = item.type != BaseItemKind.MOVIE
					val response = api.libraryApi.getThemeMedia(
						userId = userId,
						itemId = item.id,
						inheritFromParent = inheritFromParent
					)

					val tracks = response.content.themeSongsResult?.items

					if (!tracks.isNullOrEmpty()) {
						val selectedTrack = tracks.random()
						val streamUrl = buildStreamUrl(selectedTrack.id)

						if (streamUrl != null) {
							scope.launch(Dispatchers.Main) {
								initializeAndPlay(streamUrl)
							}
							return@launch
						}
					} else {
						Timber.d("No theme songs found on Jellyfin server for: ${item.name}")
					}
				}

				if (useArchiveFallback && userSettingPreferences[userSettingPreferences.themeSongsArchiveFallback]) {
					val archiveUrl = archiveHelper.getThemeSongUrl(item)
					if (archiveUrl != null) {
						scope.launch(Dispatchers.Main) {
							initializeAndPlay(archiveUrl)
						}
					} else {
						Timber.d("No theme song found on Archive.org for: ${item.name}")
					}
				} else if (!userSettingPreferences[userSettingPreferences.themeSongsArchiveFallback]) {
				}
			} catch (e: Exception) {
				e.printStackTrace()
				Timber.e(e, "Error getting theme song from Jellyfin for: ${item.name}")
				if (useArchiveFallback && userSettingPreferences[userSettingPreferences.themeSongsArchiveFallback]) {
					val archiveUrl = archiveHelper.getThemeSongUrl(item)
					if (archiveUrl != null) {
						scope.launch(Dispatchers.Main) {
							initializeAndPlay(archiveUrl)
						}
					} else {
						Timber.d("No theme song found on Archive.org for: ${item.name}")
					}
				}
			}
		}
	}

	fun fadeOutAndStop() {
		val currentPlayer = player ?: return

		fleeting?.cancel()
		fleeting = scope.launch {
			val steps = (FLEETING_DURATION / FLEETING_STEP).toInt()
			val volume = getUserVolume()

			for (i in steps downTo 0) {
				if (player == null) break
				val level = (i.toFloat() / steps) * volume
				currentPlayer.setVolume(level, level)
				delay(FLEETING_STEP)
			}

			stop()
		}
	}

	fun stop() {
		fleeting?.cancel()
		player?.apply {
			try {
				if (isPlaying) stop()
				reset()
			} catch (e: IllegalStateException) {
			} finally {
				release()
			}
		}
		player = null
		activeItemId = null
	}

	private fun shouldPlayForItem(item: BaseItemDto): Boolean {
		if (!userSettingPreferences[userSettingPreferences.themeSongsEnabled]) {
			return false
		}

		return when (item.type) {
			BaseItemKind.MOVIE -> userSettingPreferences.get(userSettingPreferences.themeSongsMovies)
			BaseItemKind.SERIES -> userSettingPreferences.get(userSettingPreferences.themeSongsSeries)
			BaseItemKind.EPISODE -> userSettingPreferences.get(userSettingPreferences.themeSongsEpisodes)
			else -> false
		}
	}

	private fun getUserVolume(): Float =
		userSettingPreferences[userSettingPreferences.themesongvolume] / 100f

	private fun buildStreamUrl(trackId: UUID): String? {
		val baseUrl = api.baseUrl ?: return null
		val token = api.accessToken ?: return null
		return "$baseUrl/Audio/$trackId/stream?static=true&audioCodec=mp3&audioBitrate=$BITRATE&api_key=$token"
	}

	private fun initializeAndPlay(url: String) {
		if (url.startsWith("http://archive.org/") || url.startsWith("https://archive.org/")) {
			playArchiveUrl(url)
		} else {
			playDirectUrl(url)
		}
	}

	private fun playArchiveUrl(url: String) {
		try {
			player = MediaPlayer().apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.build()
				)

				setDataSource(url)
				isLooping = false
				setVolume(0f, 0f)

				setOnPreparedListener {
					start()
					fadeIn()
				}

				setOnErrorListener { _, what, extra ->
					Timber.e("MediaPlayer error - what: $what, extra: $extra")
					stop()
					true
				}

				setOnCompletionListener { }

				prepareAsync()
			}
		} catch (e: IOException) {
			Timber.e(e, "IOException in playArchiveUrl")
			stop()
		} catch (e: Exception) {
			Timber.e(e, "Exception in playArchiveUrl")
			stop()
		}
	}

	private fun playDirectUrl(url: String) {
		try {
			player = MediaPlayer().apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.build()
				)

				setDataSource(url)
				isLooping = false
				setVolume(0f, 0f)

				setOnPreparedListener {
					start()
					fadeIn()
				}

				setOnErrorListener { _, what, extra ->
					stop()
					true
				}

				setOnCompletionListener { }

				prepareAsync()
			}
		} catch (e: IOException) {
			Timber.e(e, "IOException in playDirectUrl")
			stop()
		} catch (e: Exception) {
			Timber.e(e, "Exception in playDirectUrl")
			stop()
		}
	}

	private fun fadeIn() {
		fleeting?.cancel()
		fleeting = scope.launch {
			val targetVolume = getUserVolume()
			val steps = (FLEETING_DURATION / FLEETING_STEP).toInt()

			for (i in 0..steps) {
				val level = (i.toFloat() / steps) * targetVolume
				player?.setVolume(level, level)
				delay(FLEETING_STEP)
			}
		}
	}
}
