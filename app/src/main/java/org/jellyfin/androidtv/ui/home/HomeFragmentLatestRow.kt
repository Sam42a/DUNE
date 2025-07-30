package org.jellyfin.androidtv.ui.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

class HomeFragmentLatestRow(
	private val userRepository: UserRepository,
	private val userViews: Collection<BaseItemDto>,
) : HomeFragmentRow {
	@SuppressLint("StringFormatInvalid")
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Get configuration (to find excluded items)
		val configuration = userRepository.currentUser.value?.configuration

		// Create a custom card presenter with no info for the Recently Added row
		val noInfoCardPresenter = CardPresenter(false, 195).apply {
			setHomeScreen(true) // Assuming we want home screen behavior for this row
			setUniformAspect(true) // Assuming we want uniform aspect ratio
		}

		// Create a list of views to include
		val latestItemsExcludes = configuration?.latestItemsExcludes.orEmpty()
		userViews
			.filterNot { item -> item.collectionType in EXCLUDED_COLLECTION_TYPES || item.id in latestItemsExcludes }
			.map { item ->
				// Create query and add it to a new row
				val request = GetLatestMediaRequest(
					fields = ItemRepository.itemFields,
					imageTypeLimit = 1,
					parentId = item.id,
					groupItems = true,
					limit = ITEM_LIMIT,
				)

				val title = if (item.name.isNullOrBlank()) {
					context.getString(R.string.lbl_latest)
				} else {
					// Format the string with the library name
					context.resources.getString(R.string.lbl_latest_in, item.name)
				}
				HomeFragmentBrowseRowDefRow(BrowseRowDef(title, request, arrayOf(ChangeTriggerType.LibraryUpdated)))
			}.forEach { row ->
				// Add row to adapter with the no-info card presenter
				row.addToRowsAdapter(context, noInfoCardPresenter, rowsAdapter)
			}
	}

	companion object {
		// Collections excluded from latest row based on app support and common sense
		private val EXCLUDED_COLLECTION_TYPES = arrayOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)

		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT = 10
	}
}
