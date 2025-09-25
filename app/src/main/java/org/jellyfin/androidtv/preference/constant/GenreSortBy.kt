package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import org.jellyfin.sdk.model.api.ItemSortBy

enum class GenreSortBy(
	override val nameRes: Int,
	val itemSortBy: ItemSortBy
) : PreferenceEnum {

	/**
	 * Sort items By default, Server Side Delivery
	 */
	DEFAULT(R.string.sort_default, ItemSortBy.DEFAULT),

	/**
	 * Sort items randomly
	 */
	RANDOM(R.string.sort_random, ItemSortBy.RANDOM),

	/**
	 * Sort items by name (A-Z)
	 */
	NAME(R.string.sort_name, ItemSortBy.SORT_NAME),

	/**
	 * Sort items by release date (newest first)
	 */
	DATE_RELEASED(R.string.sort_date_released, ItemSortBy.PRODUCTION_YEAR),

	/**
	 * Sort items by date added (newest first)
	 */
	DATE_ADDED(R.string.sort_date_added, ItemSortBy.DATE_LAST_CONTENT_ADDED),

	/**
	 * Sort items by community rating (highest first)
	 */
	RATING(R.string.sort_rating, ItemSortBy.COMMUNITY_RATING),

	/**
	 * Sort items by critic rating (highest first)
	 */
	CRITIC_RATING(R.string.sort_critic_rating, ItemSortBy.CRITIC_RATING),

	/**
	 * Sort items by play count (most played first)
	 */
	PLAY_COUNT(R.string.sort_play_count, ItemSortBy.PLAY_COUNT)
}
