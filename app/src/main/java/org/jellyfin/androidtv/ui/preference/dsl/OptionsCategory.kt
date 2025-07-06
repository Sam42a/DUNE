package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.jellyfin.androidtv.R

class OptionsCategory(
	internal val context: Context
) {
	private val nodes = mutableListOf<OptionsItem>()
	var title: String? = null

	fun setTitle(@StringRes resId: Int) {
		title = context.getString(resId)
	}

	operator fun plusAssign(item: OptionsItem) {
		nodes.add(item)
	}

	fun build(screen: PreferenceScreen, container: OptionsUpdateFunContainer): PreferenceCategory {
		return PreferenceCategory(context).also { category ->
			category.isPersistent = false
			// Apply custom style
			category.layoutResource = R.layout.preference_category_custom
			screen.addPreference(category)
			category.title = title
			nodes.forEach { node ->
				node.build(category, container)
			}
		}
	}
}
