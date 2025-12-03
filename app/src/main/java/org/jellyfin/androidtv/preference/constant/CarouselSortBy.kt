package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import org.jellyfin.sdk.model.api.ItemSortBy

enum class CarouselSortBy(
	override val nameRes: Int,
	val itemSortBy: ItemSortBy
) : PreferenceEnum {

	/**
	 * Sort items randomly
	 */
	RANDOM(R.string.sort_random, ItemSortBy.RANDOM),

	/**
	 * Sort items by favorites
	 */
	FAVORITES(R.string.lbl_favorites, ItemSortBy.IS_FAVORITE_OR_LIKED),

	/**
	 * Sort items by name (A-Z)
	 */
	NAME(R.string.sort_name, ItemSortBy.SORT_NAME),

	/**
	 * Sort items by date added (newest first)
	 */
	DATE_ADDED(R.string.sort_date_added, ItemSortBy.DATE_CREATED),

	/**
	 * Sort items by community rating (highest first)
	 */
	COMMUNITY_RATING(R.string.sort_rating, ItemSortBy.COMMUNITY_RATING),

	/**
	 * Sort items by critic rating (highest first)
	 */
	CRITIC_RATING(R.string.sort_critic_rating, ItemSortBy.CRITIC_RATING),

	/**
	 * Sort items by play count (most played first)
	 */
	PLAY_COUNT(R.string.sort_play_count, ItemSortBy.PLAY_COUNT),

	/**
	 * Sort items by release date (newest first)
	 */
	RELEASE_DATE(R.string.sort_date_released, ItemSortBy.PRODUCTION_YEAR)
}
