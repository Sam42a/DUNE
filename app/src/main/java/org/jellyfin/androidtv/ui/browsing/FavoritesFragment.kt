package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.View
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

class FavoritesFragment : EnhancedBrowseFragment() {
    private val dataRefreshService: DataRefreshService by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set title
        mTitle?.text = getString(R.string.lbl_favorites)
    }

    override fun setupQueries(rowLoader: RowLoader) {
        // Create rows for different types of favorites
        mRows = ArrayList()

        // 1. Movies
        mRows.add(createRow(
            getString(R.string.lbl_movies),
            GetItemsRequest(
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                filters = setOf(ItemFilter.IS_FAVORITE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
                recursive = true,
                limit = 20,
                fields = ItemRepository.itemFields,
                enableImages = true,
                enableUserData = true
            )
        ))

        // 2. TV Shows
        mRows.add(createRow(
            getString(R.string.lbl_tv_series),
            GetItemsRequest(
                includeItemTypes = setOf(BaseItemKind.SERIES),
                filters = setOf(ItemFilter.IS_FAVORITE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
                recursive = true,
                limit = 20,
                fields = ItemRepository.itemFields,
                enableImages = true,
                enableUserData = true
            )
        ))

        // 3. Episodes
        mRows.add(createRow(
            getString(R.string.lbl_episodes),
            GetItemsRequest(
                includeItemTypes = setOf(BaseItemKind.EPISODE),
                filters = setOf(ItemFilter.IS_FAVORITE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
                recursive = true,
                limit = 20,
                fields = ItemRepository.itemFields,
                enableImages = true,
                enableUserData = true
            )
        ))

        // 4. Collections
        mRows.add(createRow(
            getString(R.string.lbl_collections),
            GetItemsRequest(
                includeItemTypes = setOf(BaseItemKind.BOX_SET),
                filters = setOf(ItemFilter.IS_FAVORITE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
                recursive = true,
                limit = 20,
                fields = ItemRepository.itemFields,
                enableImages = true,
                enableUserData = true
            )
        ))

        // 5. Playlists & Albums (combined)
        mRows.add(createRow(
            getString(R.string.lbl_playlists_and_albums),
            GetItemsRequest(
                includeItemTypes = setOf(BaseItemKind.PLAYLIST, BaseItemKind.MUSIC_ALBUM),
                filters = setOf(ItemFilter.IS_FAVORITE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
                recursive = true,
                limit = 20
            )
        ))

        // Load all rows
        rowLoader.loadRows(mRows)
    }

    private fun createRow(header: String, query: GetItemsRequest): BrowseRowDef {
        return BrowseRowDef(header, query, 20, false, true)
    }

    companion object {
        fun newInstance() = FavoritesFragment()
    }
}
