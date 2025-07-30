package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class HomeFragmentMusicVideosRow(
    private val userRepository: UserRepository
) : HomeFragmentRow {
    override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
        val currentUserId = userRepository.currentUser.value?.id ?: return

        // Create a query to get music videos
        val query = GetItemsRequest(
            userId = currentUserId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_VIDEO),
			sortBy = setOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
            sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
            limit = 15,
            recursive = true,
            imageTypeLimit = 1,
            enableTotalRecordCount = false,
            fields = ItemRepository.itemFields,
            enableImages = true
        )

        // Create a card presenter for music videos with specific dimensions to match collections row
        val musicVideoCardPresenter = object : CardPresenter(false, ImageType.THUMB , 165) {
            init {
                setHomeScreen(true)
                setUniformAspect(true)
            }

            override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
                super.onBindViewHolder(viewHolder, item)

                // Set fixed dimensions for all cards in the row
                (viewHolder.view as? org.jellyfin.androidtv.ui.card.LegacyImageCardView)?.let { cardView ->
                    cardView.setMainImageDimensions(235, 135)
                    cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER
                }
            }
        }

        // Add the row with our custom card presenter
        // Create the row definition with chunk size and change triggers
        val rowDef = BrowseRowDef(
            context.getString(R.string.music_videos),
            query,
            15, // chunkSize
            false, // preferParentThumb
            true, // staticHeight
            arrayOf(ChangeTriggerType.MusicPlayback)
        )

        // Add the row to the adapter
        HomeFragmentBrowseRowDefRow(rowDef).addToRowsAdapter(context, musicVideoCardPresenter, rowsAdapter)
    }
}
