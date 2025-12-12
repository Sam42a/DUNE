package org.jellyfin.androidtv.ui.home

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import androidx.leanback.widget.Row
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.card.LegacyImageCardView
import androidx.leanback.widget.BaseCardView
import timber.log.Timber

class HomeFragmentSuggestedMoviesFragmentRow(
    private val userRepository: UserRepository,
    private val api: ApiClient
) : HomeFragmentRow {

    private val noInfoCardPresenter = object : CardPresenter(false, ImageType.THUMB, 110) {
        init {
            setHomeScreen(true)
            setUniformAspect(true)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
            super.onBindViewHolder(viewHolder, item)

            (viewHolder.view as? LegacyImageCardView)?.let { cardView ->
                cardView.cardType = BaseCardView.CARD_TYPE_MAIN_ONLY
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
	override fun addToRowsAdapter(
        context: Context,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>
    ) {
        val userId = userRepository.currentUser.value?.id
        if (userId == null) {
            Timber.e("User not available, cannot load suggested movies")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.itemsApi.getItems(
                    userId = userId,
                    includeItemTypes = setOf(BaseItemKind.MOVIE),
                    sortOrder = setOf(SortOrder.DESCENDING,),
                    sortBy = setOf(ItemSortBy.DATE_PLAYED),
                    limit = 4,
                    recursive = true,
                    fields = setOf(
                        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ItemFields.OVERVIEW,
                        ItemFields.CHILD_COUNT,
                        ItemFields.MEDIA_STREAMS,
                        ItemFields.MEDIA_SOURCES
                    )
                )

                Timber.d("Got recently played movies response: ${response.content.items.size} items")

                withContext(Dispatchers.Main) {
                    for (item in response.content.items) {
                        val similarRequest = GetSimilarItemsRequest(
                            itemId = item.id,
                            fields = setOf(
                                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                ItemFields.OVERVIEW,
                                ItemFields.CHILD_COUNT,
                                ItemFields.MEDIA_STREAMS,
                                ItemFields.MEDIA_SOURCES
                            ),
                            limit = 5,
                        )

                        val rowDef = BrowseRowDef(
                            context.getString(R.string.because_you_watched, item.name),
                            similarRequest,
                            QueryType.SimilarMovies
                        )

                        val row = HomeFragmentBrowseRowDefRow(rowDef)
                        row.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading suggested movies: ${e.message}")
            }
        }
    }
}
