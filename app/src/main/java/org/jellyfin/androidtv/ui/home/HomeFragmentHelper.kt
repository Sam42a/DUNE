package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import androidx.leanback.widget.BaseCardView
import org.jellyfin.androidtv.ui.card.LegacyImageCardView
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.androidtv.ui.home.HomeFragmentMusicVideosRow

class HomeFragmentHelper(
    private val context: Context,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val ITEM_LIMIT = 40
        private const val ITEM_LIMIT_RESUME = 50
        private const val ITEM_LIMIT_RECORDINGS = 40
        private const val ITEM_LIMIT_NEXT_UP = 50
        private const val ITEM_LIMIT_ON_NOW = 20
    }

    fun loadMusicRow(): HomeFragmentRow {
		val currentUserId = userRepository.currentUser.value?.id
        val musicPlaylistQuery = GetItemsRequest(
            userId = currentUserId,
            includeItemTypes = setOf(BaseItemKind.PLAYLIST),
            mediaTypes = setOf(MediaType.AUDIO),
            sortBy = setOf(ItemSortBy.SORT_NAME),
            limit = ITEM_LIMIT,
            fields = ItemRepository.itemFields,
            recursive = true,
            excludeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)
        )
        return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_music_playlists), musicPlaylistQuery, 50))
    }

    fun loadMusicVideosRow(): HomeFragmentRow {
        return HomeFragmentMusicVideosRow(userRepository)
    }


    fun loadRecentlyAdded(userViews: Collection<org.jellyfin.sdk.model.api.BaseItemDto>): HomeFragmentRow {
        return HomeFragmentLatestRow(userRepository, userViews)
    }

    fun loadResumeVideo(): HomeFragmentRow {
        return loadResume(context.getString(R.string.lbl_continue_watching), listOf(MediaType.VIDEO))
    }

    fun loadResumeAudio(): HomeFragmentRow {
        return loadResume(context.getString(R.string.continue_listening), listOf(MediaType.AUDIO))
    }

    fun loadLatestLiveTvRecordings(): HomeFragmentRow {
        val query = GetRecordingsRequest(
            fields = ItemRepository.itemFields,
            enableImages = true,
            limit = ITEM_LIMIT_RECORDINGS
        )

        return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_recordings), query))
    }

    fun loadNextUp(): HomeFragmentRow {
        val query = GetNextUpRequest(
            imageTypeLimit = 1,
            limit = ITEM_LIMIT_NEXT_UP,
            enableResumable = false,
            fields = ItemRepository.itemFields
        )

        // Check if series thumbnails are enabled
        val useSeriesThumbnails = userPreferences[UserPreferences.seriesThumbnailsEnabled]

        //  custom row that will handle episode cards with consistent sizing
        return object : HomeFragmentRow {
            override fun addToRowsAdapter(
                context: Context,
                cardPresenter: org.jellyfin.androidtv.ui.presentation.CardPresenter,
                rowsAdapter: org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter<androidx.leanback.widget.Row>
            ) {
                // custom card presenter that hides info below cards
                val customCardPresenter = object : CardPresenter(
                    false,  // showInfo - set to false to hide info below cards
                    if (useSeriesThumbnails) ImageType.THUMB else ImageType.POSTER,  // Use THUMB or POSTER based on preference
					170  // Standard height for no-info cards
                ) {
                    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
                        super.onBindViewHolder(viewHolder, item)

                        // Set fixed dimensions for all cards in the row
                        (viewHolder.view as? LegacyImageCardView)?.let { cardView ->
                            cardView.setMainImageDimensions(220, 128) // Standard card dimensions for episode cards
                            // Set card type to not show info below
                            cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA
                        }
                    }
                }.apply {
                    setHomeScreen(true)
                    setUniformAspect(true)
                }

                HomeFragmentBrowseRowDefRow(BrowseRowDef(
                    context.getString(R.string.lbl_next_up),
                    query,
                    arrayOf(ChangeTriggerType.TvPlayback)
                )).addToRowsAdapter(context, customCardPresenter, rowsAdapter)
            }
        }
    }

    fun loadOnNow(): HomeFragmentRow {
        val query = GetRecommendedProgramsRequest(
            isAiring = true,
            fields = ItemRepository.itemFields,
            imageTypeLimit = 1,
            enableTotalRecordCount = false,
            limit = ITEM_LIMIT_ON_NOW
        )

        return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query))
    }

    private fun loadResume(title: String, includeMediaTypes: Collection<MediaType>): HomeFragmentRow {
        val query = GetResumeItemsRequest(
            limit = ITEM_LIMIT_RESUME,
            fields = ItemRepository.itemFields,
            imageTypeLimit = 1,
            enableTotalRecordCount = false,
            mediaTypes = includeMediaTypes.toList(),
            excludeItemTypes = listOf(BaseItemKind.AUDIO_BOOK)
        )

        // Check if series thumbnails are enabled
        val useSeriesThumbnails = userPreferences[UserPreferences.seriesThumbnailsEnabled]

        // custom row that will handle movie and episode cards differently
        return object : HomeFragmentRow {
            override fun addToRowsAdapter(
                context: Context,
                cardPresenter: CardPresenter,
                rowsAdapter: MutableObjectAdapter<Row>
            ) {
                // Create a custom card presenter that shows info below cards
                val continueWatchingPresenter = object : CardPresenter(
                    true,  // showInfo - set to true to show info below cards
                    if (useSeriesThumbnails) ImageType.THUMB else ImageType.THUMB,  // Use THUMB or POSTER etc based on preference
					220  // staticHeight
                ) {
                    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
                        super.onBindViewHolder(viewHolder, item)

                        // Set fixed dimensions for all cards in the rows
                        (viewHolder.view as? LegacyImageCardView)?.let { cardView ->
                            cardView.setMainImageDimensions(220, 128) // Standard card dimensions for episode cards
                            // Set card type to show info below
                            cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER
                        }
                    }
                }.apply {
                    setHomeScreen(true)
                    setUniformAspect(true)
                }
                HomeFragmentBrowseRowDefRow(BrowseRowDef(
                    title,
                    query,
                    0,
                    useSeriesThumbnails,
                    true,
                    arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)
                )).addToRowsAdapter(context, continueWatchingPresenter, rowsAdapter)
            }
        }
    }

    // Helper function to create a row with no info card style
    private fun createNoInfoRow(row: HomeFragmentRow): HomeFragmentRow {
        return object : HomeFragmentRow {
            override fun addToRowsAdapter(
                context: Context,
                cardPresenter: CardPresenter,
                rowsAdapter: MutableObjectAdapter<Row>
            ) {
                val noInfoCardPresenter = CardPresenter(false, 170).apply {
                    setHomeScreen(true)
                    setUniformAspect(true)
                }

                row.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
            }
        }
    }
}

