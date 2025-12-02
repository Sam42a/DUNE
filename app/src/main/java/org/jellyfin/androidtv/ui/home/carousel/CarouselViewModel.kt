package org.jellyfin.androidtv.ui.home.carousel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

class CarouselViewModel(
    private val api: ApiClient,
    private val imageHelper: ImageHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<CarouselUiState>(CarouselUiState.Loading)
    val uiState: StateFlow<CarouselUiState> = _uiState.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    init {
        loadFeaturedItems()
    }

    fun loadFeaturedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = CarouselUiState.Loading

                val response = api.itemsApi.getItems(
                    request = GetItemsRequest(
                        parentId = null,
                        includeItemTypes = setOf(org.jellyfin.sdk.model.api.BaseItemKind.MOVIE),
						filters = setOf(ItemFilter.IS_UNPLAYED),
						sortBy = setOf(ItemSortBy.DATE_CREATED),
                        sortOrder = setOf(SortOrder.DESCENDING),
                        limit = 15,
                        recursive = true,
                        enableImageTypes = setOf(
                        org.jellyfin.sdk.model.api.ImageType.BACKDROP,
                        org.jellyfin.sdk.model.api.ImageType.THUMB
                    ),
                        fields = setOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
                        enableTotalRecordCount = false
                    )
                )

                val items = response.content.items ?: emptyList()

                Timber.d("Processing ${items.size} items for carousel")
                val carouselItems = items.mapNotNull { item ->
                    try {
                        // Debug logging to check overview field
                        Timber.d("Item: ${item.name} - Overview: ${item.overview?.take(50) ?: "NULL"}")
                        CarouselItem.fromBaseItemDto(item, imageHelper, api)
                    } catch (e: Exception) {
                        Timber.e(e, "Error creating carousel item from: ${item.name}")
                        null
                    }
                }

                Timber.d("Successfully created ${carouselItems.count()} carousel items")

                withContext(Dispatchers.Main) {
                    _uiState.value = if (carouselItems.isNotEmpty()) {
                        CarouselUiState.Success(carouselItems)
                    } else {
                        CarouselUiState.Empty
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to load featured items for carousel")
                withContext(Dispatchers.Main) {
                    _uiState.value = CarouselUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun setActiveIndex(index: Int) {
        val currentState = _uiState.value
        if (currentState is CarouselUiState.Success) {
            val validIndex = index.coerceIn(0, currentState.items.size - 1)
            _activeIndex.value = validIndex
        }
    }

    fun getActiveItem(): CarouselItem? {
        val currentState = _uiState.value
        return if (currentState is CarouselUiState.Success) {
            val index = _activeIndex.value.coerceIn(0, currentState.items.size - 1)
            currentState.items.getOrNull(index)
        } else null
    }

    fun refresh() {
        loadFeaturedItems()
    }
}

sealed class CarouselUiState {
    object Loading : CarouselUiState()
    data class Success(val items: List<CarouselItem>) : CarouselUiState()
    object Empty : CarouselUiState()
    data class Error(val message: String) : CarouselUiState()
}
