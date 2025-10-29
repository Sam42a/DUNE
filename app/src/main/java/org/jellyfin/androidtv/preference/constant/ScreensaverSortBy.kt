package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import org.jellyfin.sdk.model.api.ItemSortBy

enum class ScreensaverSortBy(
	override val nameRes: Int,
	val itemSortBy: ItemSortBy
) : PreferenceEnum {

	RANDOM(R.string.sort_random, ItemSortBy.RANDOM),

	NAME(R.string.sort_name, ItemSortBy.SORT_NAME),

	DATE_RELEASED(R.string.sort_date_released, ItemSortBy.PRODUCTION_YEAR),

	DATE_ADDED(R.string.sort_date_added, ItemSortBy.DATE_LAST_CONTENT_ADDED),

	RATING(R.string.sort_rating, ItemSortBy.COMMUNITY_RATING),

	CRITIC_RATING(R.string.sort_critic_rating, ItemSortBy.CRITIC_RATING),

	PLAY_COUNT(R.string.sort_play_count, ItemSortBy.PLAY_COUNT)
}
