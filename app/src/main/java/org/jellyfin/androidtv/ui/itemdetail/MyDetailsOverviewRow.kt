package org.jellyfin.androidtv.ui.itemdetail

import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import android.widget.FrameLayout
import org.jellyfin.androidtv.data.model.InfoItem
import org.jellyfin.sdk.model.api.BaseItemDto

class MyDetailsOverviewRow @JvmOverloads constructor(
	val item: BaseItemDto,
	var imageDrawable: String? = null,
	var summary: String? = null,
	var infoItem1: InfoItem? = null,
	var infoItem2: InfoItem? = null,
	var infoItem3: InfoItem? = null,
	var selectedMediaSourceIndex: Int = 0,
) : Row() {
	private val _actions = mutableListOf<FrameLayout>()
	val actions get() = _actions.toList()
	val visibleActions get() = _actions.count { it.isVisible }

	fun clearActions() = _actions.clear()
	fun addAction(button: FrameLayout) = _actions.add(button)
}
