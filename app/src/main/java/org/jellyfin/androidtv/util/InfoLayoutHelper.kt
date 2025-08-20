package org.jellyfin.androidtv.util

import android.content.Context
import android.widget.LinearLayout
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRowView
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo

object InfoLayoutHelper {
	@JvmStatic
	fun addInfoRow(
		context: Context,
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		layout: LinearLayout,
		includeRuntime: Boolean,
		showMediaDetails: Boolean = false
	) {
		// Find existing BaseItemInfoRowView or create a new one
		var baseItemInfoRowView: BaseItemInfoRowView? = null

		for (i in 0 until layout.childCount) {
			val child = layout.getChildAt(i)

			if (child is BaseItemInfoRowView) {
				baseItemInfoRowView = child
				break
			}
		}

		if (baseItemInfoRowView == null) {
			baseItemInfoRowView = BaseItemInfoRowView(context)
			layout.addView(baseItemInfoRowView)
		}

		// Update item info
		baseItemInfoRowView.item = item
		baseItemInfoRowView.mediaSource = mediaSource ?: item?.mediaSources?.firstOrNull()
		baseItemInfoRowView.includeRuntime = includeRuntime
		baseItemInfoRowView.showMediaDetails = showMediaDetails
	}

	@JvmStatic
	fun addInfoRow(
		context: Context,
		item: BaseItemDto?,
		layout: LinearLayout,
		includeRuntime: Boolean,
		showMediaDetails: Boolean = false
	) = addInfoRow(context, item, null, layout, includeRuntime, showMediaDetails)
}
