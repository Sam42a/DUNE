package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber

/**
 * Custom adapter that combines resume and next up items into a single row.
 * It loads both data sources in parallel and merges them.
 */
class CombinedResumeNextUpAdapter(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    cardPresenter: CardPresenter,
    rowsAdapter: MutableObjectAdapter<Row>,
    private val apiClient: ApiClient
) : ItemRowAdapter(null, null, 0, userPreferences[UserPreferences.seriesThumbnailsEnabled], cardPresenter, rowsAdapter) {

    private var row: ListRow? = null

    companion object {
        private const val RESUME_LIMIT = 25
        private const val NEXT_UP_LIMIT = 25
        private const val TOTAL_LIMIT = 50
    }

    override fun setRow(row: ListRow) {
        this.row = row
    }

    override fun Retrieve() {
        loadData()
    }

    override fun ReRetrieveIfNeeded(): Boolean {
        loadData()
        return true
    }

    fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resumeDeferred = async { loadResumeItems() }
                val nextUpDeferred = async { loadNextUpItems() }
                val resumeItems = resumeDeferred.await()
                val nextUpItems = nextUpDeferred.await()
                val combinedItems = combineAndSortItems(resumeItems, nextUpItems)

                withContext(Dispatchers.Main) {
                    updateItems(combinedItems)
                }

                Timber.d("CombinedContinueWatching: Loaded ${combinedItems.size} items (${resumeItems.size} resume + ${nextUpItems.size} next up)")

            } catch (e: Exception) {
                Timber.e(e, "Error loading combined continue watching data")
            }
        }
    }

    private suspend fun loadResumeItems(): List<BaseItemDto> {
        return try {
            val currentUserId = userRepository.currentUser.value?.id ?: return emptyList()
            val response = apiClient.itemsApi.getResumeItems(
                GetResumeItemsRequest(
                    userId = currentUserId,
                    limit = RESUME_LIMIT,
                    fields = ItemRepository.itemFields,
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                    mediaTypes = listOf(MediaType.VIDEO),
                    excludeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.AUDIO_BOOK)
                )
            ).content
            response.items ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error loading resume items")
            emptyList()
        }
    }

    private suspend fun loadNextUpItems(): List<BaseItemDto> {
        return try {
            val currentUserId = userRepository.currentUser.value?.id ?: return emptyList()
            val response = apiClient.tvShowsApi.getNextUp(
                GetNextUpRequest(
                    userId = currentUserId,
                    limit = NEXT_UP_LIMIT,
                    enableResumable = false,
                    fields = ItemRepository.itemFields,
                    imageTypeLimit = 1
                )
            ).content
            response.items ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error loading next up items")
            emptyList()
        }
    }

    private fun combineAndSortItems(
        resumeItems: List<BaseItemDto>,
        nextUpItems: List<BaseItemDto>
    ): List<BaseItemDto> {
        val allItems = mutableListOf<BaseItemDto>()

        allItems.addAll(resumeItems)
        allItems.addAll(nextUpItems)

        // server default sorting:
        val sortedItems = allItems.sortedWith(compareBy<BaseItemDto> { item ->
            if (resumeItems.contains(item)) 0 else 1
        })

        return sortedItems.take(TOTAL_LIMIT)
    }
    private fun updateItems(items: List<BaseItemDto>) {
        val baseRowItems = items.mapNotNull { item ->
            try {
                BaseItemDtoBaseRowItem(item, preferParentThumb, false)
            } catch (e: Exception) {
                Timber.e(e, "Error creating BaseRowItem for item: ${item.name}")
                null
            }
        }

        replaceAll(baseRowItems)
    }
}
