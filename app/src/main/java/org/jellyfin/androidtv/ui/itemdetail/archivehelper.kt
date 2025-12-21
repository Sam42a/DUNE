package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Archive.org Theme Song Helper
 *
 * Provides fallback theme song discovery by searching Internet Archive's public collections.
 * This helper is used when theme songs are not available in the local Jellyfin library.
 *
 * Data Source: Internet Archive (archive.org)
 * - A non-profit digital library providing universal access to knowledge
 * - Only accesses publicly available, open collections
 * - Content is streamed directly to users, not redistributed
 *
 * Technical Notes:
 * - Rate limited to 1 request/second to be respectful of their free service
 * - Each app instance makes its own requests (distributed load)
 */
class ArchiveHelper(private val context: Context) {

	companion object {
		private const val USER_AGENT = "AndroidTV/1.0"
		private const val CONNECT_TIMEOUT_MS = 10000
		private const val READ_TIMEOUT_MS = 15000
		private const val MAX_RETRIES = 1
		private const val RETRY_DELAY_MS = 1000L

		private val THEME_SONG_COLLECTIONS = listOf(
			"televisiontunes",
			"tvtunes",
			"animetheme",
			"anime-themes-from-60s-to-2013-ops-and-ends",
			"TVThemeSongsTVThemesChipnDaleRescueRangers",
			"full-length-tv-themes",
			"gameshowthemesongsnet-game-show-theme-song-collection",
			"childrens-tv-themes",
			"ThemeOfNightTrainToLisbon"
		)

		private val ALLOWED_AUDIO_FORMATS = setOf("mp3", "wav", "m4a", "ogg", "flac")
		private const val MAX_RESULTS = 50
	}

	private var lastRequestTime = 0L
	private val minRequestInterval = 1000L

	suspend fun getThemeSongUrl(item: BaseItemDto): String? = withContext(Dispatchers.IO) {
		try {
			val targetName = extractTargetName(item)

			if (targetName.isBlank() || targetName.length < 2) {
				return@withContext null
			}

			for (collection in THEME_SONG_COLLECTIONS) {
				val result = searchCollection(targetName, collection)
				if (result != null) {
					return@withContext result
				}
			}

			return@withContext null
		} catch (e: Exception) {
			Timber.e(e, "Error searching theme song")
			null
		}
	}

	private suspend fun searchCollection(seriesName: String, collection: String): String? {
		return try {
			val sanitizedSeriesName = sanitizeInput(seriesName)

			val query = buildString {
				append("\"$sanitizedSeriesName\" ")
				append("collection:$collection ")
				append("mediatype:audio ")
				append("-mediatype:collection ")
				append("-mediatype:web")
			}

			val results = executeArchiveSearch(query)

			if (results != null) {
				val items = results.getJSONArray("items")

				for (i in 0 until minOf(items.length(), MAX_RESULTS)) {
					val item = items.getJSONObject(i)
					val identifier = item.getString("identifier")
					val title = item.optString("title", "")

					if (isValidIdentifier(identifier) && isValidThemeSong(title, seriesName)) {
						return getAudioUrl(identifier)
					}
				}
			}

			null
		} catch (e: Exception) {
			Timber.e(e, "Error searching collection $collection")
			null
		}
	}

	private suspend fun executeArchiveSearch(query: String): JSONObject? = withContext(Dispatchers.IO) {
		var attempt = 0
		var lastException: Exception? = null

		while (attempt < MAX_RETRIES) {
			try {
				rateLimit()

				val encodedQuery = URLEncoder.encode(query, "UTF-8")
				val searchUrl = "http://archive.org/advancedsearch.php?" +
					"q=$encodedQuery" +
					"&fl[]=identifier" +
					"&fl[]=title" +
					"&fl[]=creator" +
					"&fl[]=date" +
					"&fl[]=downloads" +
					"&output=json" +
					"&rows=$MAX_RESULTS" +
					"&sort[]=downloads+desc"

				if (!isValidArchiveUrl(searchUrl)) {
					return@withContext null
				}

				val connection = URL(searchUrl).openConnection() as HttpURLConnection
				configureConnection(connection)

				val responseCode = connection.responseCode

				if (responseCode == 200) {
					val response = connection.inputStream.bufferedReader().use { it.readText() }

					if (response.length > 10_000_000) {
						Timber.w("Response too large, rejecting")
						return@withContext null
					}

					val jsonResponse = JSONObject(response)
					val responseItems = jsonResponse.optJSONObject("response")?.optJSONArray("docs")

					if (responseItems != null && responseItems.length() > 0) {
						val result = JSONObject()
						result.put("items", responseItems)
						result.put("count", responseItems.length())
						return@withContext result
					}
				} else if (responseCode == 429) {
					kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
					attempt++
					continue
				}

				return@withContext null
			} catch (e: Exception) {
				lastException = e
				attempt++
				if (attempt < MAX_RETRIES) {
					kotlinx.coroutines.delay(RETRY_DELAY_MS)
				}
			}
		}

		Timber.e(lastException, "API request failed after $MAX_RETRIES attempts")
		null
	}

	private fun isValidThemeSong(title: String, requestedSeries: String): Boolean {
		val titleLower = title.lowercase().trim()
		val seriesLower = requestedSeries.lowercase().trim()

		if (seriesLower.length < 3 || titleLower.length > 200) {
			return false
		}

		val hasThemeSongIndicators = listOf(
			"theme song", "theme", "opening", "title song", "main title", "intro", "soundtrack", "music",
			"main theme", "tv theme", "television theme", "opening theme", "show theme",
			"series theme", "title theme", "signature tune", "opening credits", "opening song",
			"theme music", "original theme", "tv song", "television song", "program theme"
		).any { indicator -> titleLower.contains(indicator) }

		val isEndingTheme = listOf(
			"ending", "closing", "credits", "outro", "finale", "epilogue"
		).any { indicator -> titleLower.contains(indicator) }

		val containsSeriesName = when {
			seriesLower.length >= 4 -> {
				val cleanSeries = seriesLower.replace(Regex("[^a-z0-9\\s]"), "")
				val cleanTitle = titleLower.replace(Regex("[^a-z0-9\\s]"), "")

				cleanTitle.contains("\\b$cleanSeries\\b".toRegex()) ||
					titleLower.contains("$seriesLower theme") ||
					titleLower.contains("$seriesLower -") ||
					titleLower.contains("- $seriesLower") ||
					titleLower.contains(seriesLower)
			}
			seriesLower.length == 3 -> {
				titleLower.contains("$seriesLower theme") ||
					titleLower.contains("$seriesLower -") ||
					titleLower.contains("- $seriesLower")
			}
			else -> false
		}

		val isComplexPattern = titleLower.split(" - ").size > 2

		val isTooGeneric = listOf("the", "and", "of", "in", "on", "at", "to", "for").count {
			titleLower.split("\\s+".toRegex()).contains(it)
		} > 3

		return hasThemeSongIndicators &&
			!isEndingTheme &&
			containsSeriesName &&
			!isComplexPattern &&
			!isTooGeneric
	}

	private suspend fun getAudioUrl(identifier: String): String? = withContext(Dispatchers.IO) {
		if (!isValidIdentifier(identifier)) {
			return@withContext null
		}

		try {
			rateLimit()

			val metadataUrl = "http://archive.org/metadata/$identifier"

			if (!isValidArchiveUrl(metadataUrl)) {
				return@withContext null
			}

			val connection = URL(metadataUrl).openConnection() as HttpURLConnection
			configureConnection(connection)

			if (connection.responseCode == 200) {
				val response = connection.inputStream.bufferedReader().use { it.readText() }

				if (response.length > 5_000_000) {
					Timber.w("Metadata response too large, rejecting")
					return@withContext null
				}

				val metadata = JSONObject(response)
				val files = metadata.getJSONArray("files")

				for (i in 0 until files.length()) {
					val file = files.getJSONObject(i)
					val name = file.getString("name")
					val format = file.optString("format", "")

					if (isAudioFile(name, format)) {
						val sanitizedName = sanitizeFilename(name)
						val downloadUrl = "http://archive.org/download/$identifier/$sanitizedName"

						if (isValidArchiveUrl(downloadUrl)) {
							return@withContext downloadUrl
						}
					}
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "Error getting audio URL for: $identifier")
		}

		null
	}

	private fun extractTargetName(item: BaseItemDto): String {
		val seriesName = item.seriesName?.trim()
		val itemName = item.name?.trim()

		val targetName = when {
			!seriesName.isNullOrEmpty() -> seriesName
			!itemName.isNullOrEmpty() -> itemName
			else -> ""
		}

		return targetName.lowercase().take(100)
	}

	private fun configureConnection(connection: HttpURLConnection) {
		connection.setRequestProperty("User-Agent", USER_AGENT)
		connection.connectTimeout = CONNECT_TIMEOUT_MS
		connection.readTimeout = READ_TIMEOUT_MS
		connection.instanceFollowRedirects = true
		connection.setRequestProperty("Accept", "application/json")
	}

	private fun isValidArchiveUrl(url: String): Boolean {
		return (url.startsWith("http://archive.org/") || url.startsWith("https://archive.org/")) &&
			url.length < 2000 &&
			!url.contains("..") &&
			url.indexOf("://", startIndex = 7) == -1
	}

	private fun isValidIdentifier(identifier: String): Boolean {
		return identifier.isNotBlank() &&
			identifier.length < 200 &&
			identifier.matches(Regex("^[a-zA-Z0-9_.-]+$"))
	}

	private fun sanitizeInput(input: String): String {
		return input.trim()
			.replace(Regex("[\"'<>]"), "")
			.take(100)
	}

	private fun sanitizeFilename(filename: String): String {
		return URLEncoder.encode(filename, "UTF-8")
			.replace("+", "%20")
	}

	private fun isAudioFile(name: String, format: String): Boolean {
		val extension = name.substringAfterLast('.', "").lowercase()

		return (format.equals("MP3", ignoreCase = true) ||
			format.equals("VBR MP3", ignoreCase = true) ||
			format.contains("Audio", ignoreCase = true) ||
			ALLOWED_AUDIO_FORMATS.contains(extension)) &&
			name.length < 500
	}

	private suspend fun rateLimit() {
		val now = System.currentTimeMillis()
		val timeSinceLastRequest = now - lastRequestTime

		if (timeSinceLastRequest < minRequestInterval) {
			kotlinx.coroutines.delay(minRequestInterval - timeSinceLastRequest)
		}

		lastRequestTime = System.currentTimeMillis()
	}
}
