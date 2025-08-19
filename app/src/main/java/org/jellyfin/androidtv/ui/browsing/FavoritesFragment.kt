package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.home.HomeFragmentBrowseRowDefRow
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.home.HomeFragmentRow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

class FavoritesFragment : EnhancedBrowseFragment() {
	private val dataRefreshService: DataRefreshService by inject()

	// List of rows to display
	private lateinit var mRows: MutableList<Any>

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

		// 2. Movies Watched
		mRows.add(createRow(
			getString(R.string.lbl_Watched_History_Movies),
			GetItemsRequest(
				includeItemTypes = setOf(BaseItemKind.MOVIE),
				filters = setOf(ItemFilter.IS_PLAYED),
				sortBy = setOf(ItemSortBy.DATE_PLAYED),
				sortOrder = setOf(SortOrder.DESCENDING),
				recursive = true,
				limit = 20,
				fields = ItemRepository.itemFields,
				enableImages = true,
				enableUserData = true
			)
		))

		// 2. Series Watched
		mRows.add(createRow(
			getString(R.string.lbl_Watched_History_Series),
			GetItemsRequest(
				includeItemTypes = setOf(BaseItemKind.SERIES),
				filters = setOf(ItemFilter.IS_PLAYED),
				sortBy = setOf(ItemSortBy.DATE_PLAYED),
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
			header = getString(R.string.lbl_collections),
			query = GetItemsRequest(
				includeItemTypes = setOf(BaseItemKind.BOX_SET),
				filters = setOf(ItemFilter.IS_FAVORITE),
				sortBy = setOf(ItemSortBy.DATE_CREATED),
				sortOrder = setOf(SortOrder.DESCENDING),
				recursive = true,
				limit = 20,
				imageTypeLimit = 1,
				enableImageTypes = setOf(SdkImageType.THUMB, SdkImageType.BACKDROP, SdkImageType.PRIMARY),
				fields = ItemRepository.itemFields,
				enableImages = true,
				enableUserData = true
			),
			isMusicVideo = true
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

		// 4. Music Videos
		mRows.add(createRow(
			header = getString(R.string.lbl_music_videos),
			query = GetItemsRequest(
				includeItemTypes = setOf(BaseItemKind.MUSIC_VIDEO),
				filters = setOf(ItemFilter.IS_FAVORITE),
				sortBy = setOf(ItemSortBy.DATE_CREATED),
				sortOrder = setOf(SortOrder.ASCENDING),
				limit = 20,
				recursive = true,
				imageTypeLimit = 1,
				enableImageTypes = setOf(SdkImageType.THUMB,SdkImageType.BACKDROP,SdkImageType.PRIMARY),
				fields = ItemRepository.itemFields,
				enableImages = true,
				enableUserData = true
			),
			isMusicVideo = true
		))

		// Separate BrowseRowDef and HomeFragmentRow items
		val browseRowDefs = mRows.filterIsInstance<BrowseRowDef>().toMutableList()
		val homeFragmentRows = mRows.filterIsInstance<HomeFragmentRow>()

		// First load regular rows
		rowLoader.loadRows(browseRowDefs)

		// Then add custom rows
		val context = requireContext()
		val rowsAdapter = mRowsAdapter ?: return

		homeFragmentRows.forEach { row ->
			row.addToRowsAdapter(context, CardPresenter(), rowsAdapter)
		}
	}

	/**
	 *  row with custom card size for music videos and collections
	 * @param header The row header text
	 * @param query The query to fetch items
	 * @param isMusicVideo Whether to use the custom card size (also used for collections)
	 */
	private fun createRow(header: String, query: GetItemsRequest, isMusicVideo: Boolean = false): Any {
		if (!isMusicVideo) {
			return BrowseRowDef(header, query, 20, false, true)
		}

		//  music videos, custom CardPresenter with fixed dimensions
		val musicVideoCardPresenter = object : CardPresenter(false, ImageType.THUMB, 165) {
			init {
				setHomeScreen(true)
				setUniformAspect(true)
			}

			override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
				super.onBindViewHolder(viewHolder, item)

				//  fixed dimensions for all cards in the row
				(viewHolder.view as? org.jellyfin.androidtv.ui.card.LegacyImageCardView)?.let { cardView ->
					cardView.setMainImageDimensions(210, 120)
					cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA
				}
			}
		}

		//  a row definition
		val rowDef = BrowseRowDef(
			header,
			query,
			6, // chunkSize
			false, // preferParentThumb
			true, // staticHeight
			arrayOf(ChangeTriggerType.LibraryUpdated)
		)

		// Return a custom row that uses our presenter
		return object : HomeFragmentRow {
			override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
				HomeFragmentBrowseRowDefRow(rowDef).addToRowsAdapter(context, musicVideoCardPresenter, rowsAdapter)
			}
		}
	}

	companion object {
		fun newInstance() = FavoritesFragment()
	}
}
