package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

/**
 * Manages genre row loading and display for the home screen with optimized performance.
 * Features lazy loading, caching, and resource management.
 */
class GenreManager(
	private val context: Context,
	private val userRepository: UserRepository,
	private val userPreferences: UserPreferences,
	private val userSettingPreferences: UserSettingPreferences
) {

	companion object {
		private const val GENRE_ITEM_LIMIT = 10
		private const val GENRE_CARD_HEIGHT = 170
		private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
	}

	data class GenreConfig(
		val name: String,
		val displayName: String,
		val preference: org.jellyfin.preference.Preference<Boolean>,
		val loader: () -> HomeFragmentRow,
		val isNullable: Boolean = false
	)
	data class CachedEnabledGenres(
		val genres: List<GenreConfig>,
		val timestamp: Long = System.currentTimeMillis()
	) {
		fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
	}

	// Performance optimization: Lazy-loaded genre configurations
	private val genreConfigs by lazy {
		listOf(
			GenreConfig(
				name = "Collections",
				displayName = "Collections",
				preference = userSettingPreferences.showCollectionsRow,
				loader = { createCollectionsRow() }
			),
			GenreConfig(
				name = "Science Fiction",
				displayName = "Sci-Fi",
				preference = userSettingPreferences.showSciFiRow,
				loader = { createGenreRow("Science Fiction") }
			),
			GenreConfig(
				name = "Comedy",
				displayName = "Comedy",
				preference = userSettingPreferences.showComedyRow,
				loader = { createGenreRow("Comedy") }
			),
			GenreConfig(
				name = "Romance",
				displayName = "Romance",
				preference = userSettingPreferences.showRomanceRow,
				loader = { createGenreRow("Romance") }
			),
			GenreConfig(
				name = "Anime",
				displayName = "Anime",
				preference = userSettingPreferences.showAnimeRow,
				loader = { createGenreRow("Anime") }
			),
			GenreConfig(
				name = "Action",
				displayName = "Action",
				preference = userSettingPreferences.showActionRow,
				loader = { createGenreRow("Action") }
			),
			GenreConfig(
				name = "Action & Adventure",
				displayName = "Action & Adventure",
				preference = userSettingPreferences.showActionAdventureRow,
				loader = { createGenreRow("Action & Adventure") }
			),
			GenreConfig(
				name = "Documentary",
				displayName = "Documentary",
				preference = userSettingPreferences.showDocumentaryRow,
				loader = { createGenreRow("Documentary") }
			),
			GenreConfig(
				name = "Reality",
				displayName = "Reality",
				preference = userSettingPreferences.showRealityRow,
				loader = { createGenreRow("Reality") },
				isNullable = true
			),
			GenreConfig(
				name = "Family",
				displayName = "Family",
				preference = userSettingPreferences.showFamilyRow,
				loader = { createGenreRow("Family") }
			),
			GenreConfig(
				name = "Horror",
				displayName = "Horror",
				preference = userSettingPreferences.showHorrorRow,
				loader = { createGenreRow("Horror") }
			),
			GenreConfig(
				name = "Fantasy",
				displayName = "Fantasy",
				preference = userSettingPreferences.showFantasyRow,
				loader = { createGenreRow("Fantasy") }
			),
			GenreConfig(
				name = "History",
				displayName = "History",
				preference = userSettingPreferences.showHistoryRow,
				loader = { createGenreRow("History") }
			),
			GenreConfig(
				name = "Music",
				displayName = "Music",
				preference = userSettingPreferences.showMusicRow,
				loader = { createMusicRow() }
			),
			GenreConfig(
				name = "Mystery",
				displayName = "Mystery",
				preference = userSettingPreferences.showMysteryRow,
				loader = { createGenreRow("Mystery") }
			),
			GenreConfig(
				name = "Thriller",
				displayName = "Thriller",
				preference = userSettingPreferences.showThrillerRow,
				loader = { createGenreRow("Thriller") }
			),
			GenreConfig(
				name = "War",
				displayName = "War",
				preference = userSettingPreferences.showWarRow,
				loader = { createGenreRow("War") }
			)
		)
	}

	// Cache for enabled genres to avoid repeated preference access
	private var cachedEnabledGenres: CachedEnabledGenres? = null

	fun getEnabledGenres(): List<GenreConfig> {
		// Performance optimization: Use cache if valid
		val cached = cachedEnabledGenres
		return if (cached != null && !cached.isExpired()) {
			Timber.d("Using cached enabled genres (${cached.genres.size} genres)")
			cached.genres
		} else {
			Timber.d("Computing enabled genres")
			val enabled = genreConfigs.filter { config ->
				userSettingPreferences[config.preference]
			}
			cachedEnabledGenres = CachedEnabledGenres(enabled)
			Timber.d("Found ${enabled.size} enabled genres")
			enabled
		}
	}

	fun clearEnabledGenresCache() {
		cachedEnabledGenres = null
		Timber.d("Cleared enabled genres cache")
	}

	suspend fun loadGenreRows(
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		try {
			val enabledGenres = getEnabledGenres()
			Timber.d("Loading ${enabledGenres.size} enabled genre rows")

			if (enabledGenres.isEmpty()) {
				Timber.d("No genre rows enabled")
				return@withContext
			}

			enabledGenres.forEach { config ->
				try {
					loadSingleGenreRow(config, cardPresenter, rowsAdapter)
				} catch (e: Exception) {
					Timber.e(e, "Error loading genre row: ${config.displayName}")
				}
			}

			val loadTime = System.currentTimeMillis() - startTime
			Timber.d("Successfully loaded ${enabledGenres.size} genre rows in ${loadTime}ms")
		} catch (e: Exception) {
			Timber.e(e, "Error loading genre rows")
		}
	}

	private suspend fun loadSingleGenreRow(
		config: GenreConfig,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) {
		val rowStartTime = System.currentTimeMillis()
		try {
			Timber.d("Loading ${config.displayName} row")

			val row = config.loader()

			if (config.isNullable && row == null) {
				Timber.d("No matching ${config.displayName} genre found in library")
				return
			}

			withContext(Dispatchers.Main) {
				row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}

			val rowLoadTime = System.currentTimeMillis() - rowStartTime
			Timber.d("Successfully added ${config.displayName} row in ${rowLoadTime}ms")

		} catch (e: Exception) {
			Timber.e(e, "Error loading ${config.displayName} row")
		}
	}

	private fun createGenreRow(genreName: String): HomeFragmentRow {
		val query = GetItemsRequest(
			userId = userRepository.currentUser.value?.id,
			includeItemTypes = listOf(
				BaseItemKind.MOVIE,
				BaseItemKind.SERIES,
				BaseItemKind.MUSIC_ALBUM,
				BaseItemKind.MUSIC_ARTIST,
				BaseItemKind.MUSIC_VIDEO
			),
			excludeItemTypes = listOf(BaseItemKind.EPISODE),
			genres = listOf(genreName),
			sortBy = listOf(userPreferences[UserPreferences.genreSortBy].itemSortBy),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = GENRE_ITEM_LIMIT,
			recursive = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				val noInfoCardPresenter = CardPresenter(false, GENRE_CARD_HEIGHT).apply {
					setHomeScreen(true)
					setUniformAspect(true)
				}

				HomeFragmentBrowseRowDefRow(BrowseRowDef(genreName, query, GENRE_ITEM_LIMIT, false, true))
					.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
			}
		}
	}

	/**
	 * special music row for playlists
	 */
	private fun createMusicRow(): HomeFragmentRow {
		val currentUserId = userRepository.currentUser.value?.id
		val musicPlaylistQuery = GetItemsRequest(
			userId = currentUserId,
			includeItemTypes = setOf(BaseItemKind.PLAYLIST),
			mediaTypes = setOf(MediaType.AUDIO),
			sortBy = listOf(userPreferences[UserPreferences.genreSortBy].itemSortBy),
			limit = GENRE_ITEM_LIMIT,
			fields = ItemRepository.itemFields,
			recursive = true,
			excludeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				val noInfoCardPresenter = CardPresenter(false, GENRE_CARD_HEIGHT).apply {
					setHomeScreen(true)
					setUniformAspect(true)
				}

				HomeFragmentBrowseRowDefRow(BrowseRowDef("Music Playlists", musicPlaylistQuery, GENRE_ITEM_LIMIT, false, true))
					.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
			}
		}
	}

	/**
	 * Creates a Collections row with thumbnail images and same card size as Music Videos row
	 */
	private fun createCollectionsRow(): HomeFragmentRow {
		val currentUserId = userRepository.currentUser.value?.id
			?: throw IllegalStateException("User not available")

		// Create a query to get collections
		val query = GetItemsRequest(
			userId = currentUserId,
			includeItemTypes = listOf(BaseItemKind.BOX_SET),
			sortBy = setOf(ItemSortBy.RANDOM),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = GENRE_ITEM_LIMIT,
			recursive = true,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			fields = ItemRepository.itemFields,
			enableImages = true
		)

		return object : HomeFragmentRow {
			override fun addToRowsAdapter(
				context: Context,
				cardPresenter: CardPresenter,
				rowsAdapter: MutableObjectAdapter<Row>
			) {
				// Create a card presenter for collections with thumbnail images and specific dimensions to match Music Videos row
				val collectionsCardPresenter = object : CardPresenter(false, ImageType.THUMB, 165) {
					init {
						setHomeScreen(true)
						setUniformAspect(true)
					}

					override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
						super.onBindViewHolder(viewHolder, item)

						// Set fixed dimensions for all cards in the row (same as Music Videos)
						(viewHolder.view as? org.jellyfin.androidtv.ui.card.LegacyImageCardView)?.let { cardView ->
							cardView.setMainImageDimensions(220, 128)
							cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER
						}
					}
				}

				// Create the row definition with chunk size and change triggers
				val rowDef = BrowseRowDef(
					"Collections",
					query,
					10, // chunkSize
					false, // preferParentThumb
					true, // staticHeight
					arrayOf(ChangeTriggerType.LibraryUpdated)
				)

				// Add the row to the adapter
				HomeFragmentBrowseRowDefRow(rowDef).addToRowsAdapter(context, collectionsCardPresenter, rowsAdapter)
			}
		}
	}

	fun hasEnabledGenres(): Boolean {
		return genreConfigs.any { config ->
			userSettingPreferences[config.preference]
		}
	}

	fun getEnabledGenreCount(): Int {
		return getEnabledGenres().size
	}

	fun toggleGenreRow(genreName: String, enabled: Boolean) {
		val config = genreConfigs.find { it.name == genreName }
		if (config != null) {
			userSettingPreferences[config.preference] = enabled
			clearEnabledGenresCache() // Clear cache when preferences change
			Timber.d("Toggled genre '$genreName' to $enabled")
		} else {
			Timber.w("Genre '$genreName' not found in configurations")
		}
	}

	fun getAllGenreConfigs(): List<GenreConfig> {
		return genreConfigs.toList()
	}

	fun preloadGenreConfigs() {
		val configs = genreConfigs
		Timber.d("Preloaded ${configs.size} genre configurations")
	}

	fun isGenreEnabled(genreName: String): Boolean {
		val config = genreConfigs.find { it.name == genreName }
		return config?.let { userSettingPreferences[it.preference] } ?: false
	}

	fun getCacheStats(): Map<String, Any> {
		val cached = cachedEnabledGenres
		return mapOf(
			"has_cached_genres" to (cached != null),
			"cache_expired" to (cached?.isExpired() ?: true),
			"cached_genre_count" to (cached?.genres?.size ?: 0),
			"total_genre_configs" to genreConfigs.size,
			"cache_expiry_ms" to CACHE_EXPIRY_MS
		)
	}

	/**
	 * Force refresh of enabled genres cache
	 */
	fun refreshEnabledGenres() {
		clearEnabledGenresCache()
		getEnabledGenres() // This will recreate the cache
		Timber.d("Refreshed enabled genres cache")
	}
}
