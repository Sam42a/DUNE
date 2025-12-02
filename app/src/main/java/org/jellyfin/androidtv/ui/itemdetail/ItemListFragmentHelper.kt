package org.jellyfin.androidtv.ui.itemdetail

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

fun ItemListFragment.loadItem(itemId: UUID) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		val item = withContext(Dispatchers.IO) {
			api.userLibraryApi.getItem(itemId).content
		}
		if (isActive) setBaseItem(item)
	}
}

fun MusicFavoritesListFragment.getFavoritePlaylist(
	parentId: UUID?,
	callback: (items: List<BaseItemDto>) -> Unit
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		val result = withContext(Dispatchers.IO) {
			api.itemsApi.getItems(
				parentId = parentId,
				includeItemTypes = setOf(BaseItemKind.AUDIO),
				recursive = true,
				filters = setOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE_OR_LIKES),
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = 100,
				fields = ItemRepository.itemFields,
			).content
		}

		callback(result.items)
	}
}

fun ItemListFragment.getPlaylist(
	item: BaseItemDto,
	callback: (items: List<BaseItemDto>) -> Unit
) {
	getPlaylist(item, ItemSortBy.SORT_NAME, SortOrder.ASCENDING, callback)
}

fun ItemListFragment.getPlaylist(
	item: BaseItemDto,
	sortBy: ItemSortBy,
	sortOrder: SortOrder,
	callback: (items: List<BaseItemDto>) -> Unit
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		try {
			val result = withContext(Dispatchers.IO) {
				try {
					when {
						item.type == BaseItemKind.PLAYLIST -> {
							val batchResult = api.playlistsApi.getPlaylistItems(
								playlistId = item.id,
								startIndex = 0,
								limit = 700,
								fields = ItemRepository.itemFields,
							).content

							val items = batchResult.items ?: emptyList()
							Timber.d("Loaded ${items.size} playlist items")
							items
						}
						else -> {
							val result = api.itemsApi.getItems(
								parentId = item.id,
								recursive = true,
								sortBy = setOf(sortBy),
								sortOrder = setOf(sortOrder),
								limit = 700,
								fields = ItemRepository.itemFields,
							).content
							val items = result.items ?: emptyList()
							Timber.d("Loaded ${items.size} regular items")
							items
						}
					}
				} catch (e: Exception) {
					Timber.e(e, "Error loading items")
					emptyList()
				}
			}

			// Sort the results if we have any
			val sortedResult = if (result.isNotEmpty()) {
				sortPlaylistItems(result, sortBy, sortOrder)
			} else {
				result
			}

			Timber.d("Returning ${sortedResult.size} sorted items to callback")

			withContext(Dispatchers.Main) {
				try {
					callback(sortedResult)
				} catch (e: Exception) {
					Timber.e(e, "Error in item response callback")
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "Error in getPlaylist")
			withContext(Dispatchers.Main) {
				try {
					callback(emptyList())
				} catch (e: Exception) {
					Timber.e(e, "Error in error callback")
				}
			}
		}
	}
}

fun ItemListFragment.getPlaylistBatch(
	item: BaseItemDto,
	sortBy: ItemSortBy,
	sortOrder: SortOrder,
	startIndex: Int,
	limit: Int,
	callback: (items: List<BaseItemDto>) -> Unit
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		val result = withContext(Dispatchers.IO) {
			when {
				item.type == BaseItemKind.PLAYLIST -> {
					try {
						val batchResult = api.playlistsApi.getPlaylistItems(
							playlistId = item.id,
							startIndex = startIndex,
							limit = limit,
							fields = ItemRepository.itemFields,
						).content

						batchResult.items ?: emptyList()
					} catch (e: Exception) {
						emptyList()
					}
				}

				else -> api.itemsApi.getItems(
					parentId = item.id,
					recursive = true,
					sortBy = setOf(sortBy),
					sortOrder = setOf(sortOrder),
					startIndex = startIndex,
					limit = limit,
					fields = ItemRepository.itemFields,
				).content.items ?: emptyList()
			}
		}

		callback(result)
	}
}

fun ItemListFragment.toggleFavorite(item: BaseItemDto, callback: (item: BaseItemDto) -> Unit) {
	val itemMutationRepository by inject<ItemMutationRepository>()

	lifecycleScope.launch {
		val userData = itemMutationRepository.setFavorite(
			item = item.id,
			favorite = !(item.userData?.isFavorite ?: false)
		)
		callback(item.copy(userData = userData))
	}
}

private fun sortPlaylistItems(
	items: List<BaseItemDto>,
	sortBy: ItemSortBy,
	sortOrder: SortOrder
): List<BaseItemDto> {
	val sorted = when (sortBy) {
		ItemSortBy.SORT_NAME -> items.sortedBy { it.name?.lowercase() ?: "" }
		ItemSortBy.DATE_CREATED -> items.sortedBy { it.dateCreated }
		ItemSortBy.PREMIERE_DATE -> items.sortedBy { it.premiereDate }
		ItemSortBy.AIRED_EPISODE_ORDER -> items.sortedBy { it.airTime }
		ItemSortBy.CRITIC_RATING -> items.sortedBy { it.criticRating ?: 0f }
		else -> items.sortedBy { it.name?.lowercase() ?: "" }
	}

	return if (sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
}
