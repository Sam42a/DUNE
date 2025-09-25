package org.jellyfin.androidtv.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.AnyRes
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.albumPrimaryImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UserDto

class ImageHelper(
	private val api: ApiClient,
	private val userPreferences: UserPreferences,
	private val context: Context
) {
	companion object {
		const val ASPECT_RATIO_2_3 = 2.0 / 3.0
		const val ASPECT_RATIO_16_9 = 16.0 / 9.0
		const val ASPECT_RATIO_7_9 = 7.0 / 9.0
		const val ASPECT_RATIO_BANNER = 1000.0 / 185.0

		// Default max height for standard definition
		private const val MAX_IMAGE_HEIGHT_SD = 720
		// Max height for high definition
		private const val MAX_IMAGE_HEIGHT_HD = 1080
		// Max height for 4K UHD
		private const val MAX_IMAGE_HEIGHT_4K = 2160

		// Multipliers for different quality settings
		private const val QUALITY_MULTIPLIER_LOW = 0.75f
		private const val QUALITY_MULTIPLIER_MEDIUM = 1.0f
		private const val QUALITY_MULTIPLIER_HIGH = 1.5f
	}

	private fun getQualityMultiplier(): Float = when (userPreferences[UserPreferences.imageQuality]) {
		"low" -> QUALITY_MULTIPLIER_LOW
		"high" -> QUALITY_MULTIPLIER_HIGH
		else -> QUALITY_MULTIPLIER_MEDIUM // default to medium
	}

	fun getMaxImageHeight(): Int {
		val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		val displayMetrics = DisplayMetrics()
		windowManager.defaultDisplay.getMetrics(displayMetrics)

		val screenHeight = displayMetrics.heightPixels
		val screenWidth = displayMetrics.widthPixels
		val screenDensity = displayMetrics.densityDpi

		// Check if device is 4K capable
		val is4K = screenHeight >= 2160 || screenWidth >= 3840 || screenDensity >= DisplayMetrics.DENSITY_XXXHIGH

		// Base max height based on screen resolution
		val baseMaxHeight = when {
			is4K -> MAX_IMAGE_HEIGHT_4K
			screenHeight >= 1080 -> MAX_IMAGE_HEIGHT_HD
			else -> MAX_IMAGE_HEIGHT_SD
		}

		// Apply quality multiplier
		return (baseMaxHeight * getQualityMultiplier()).toInt()
	}

	fun getImageUrl(image: JellyfinImage): String = image.getUrl(api, maxHeight = getMaxImageHeight())

	fun getImageAspectRatio(item: BaseItemDto, preferParentThumb: Boolean): Double {
		if (preferParentThumb && (item.parentThumbItemId != null || item.seriesThumbImageTag != null)) {
			return ASPECT_RATIO_16_9
		}

		val primaryAspectRatio = item.primaryImageAspectRatio;
		if (item.type == BaseItemKind.EPISODE) {
			if (primaryAspectRatio != null) return primaryAspectRatio
			if (item.parentThumbItemId != null || item.seriesThumbImageTag != null) return ASPECT_RATIO_16_9
		}

		if (item.type == BaseItemKind.USER_VIEW && item.imageTags?.containsKey(ImageType.PRIMARY) == true) return ASPECT_RATIO_16_9
		return primaryAspectRatio ?: ASPECT_RATIO_7_9
	}

	fun getPrimaryImageUrl(
		item: BaseItemPerson,
		maxHeight: Int? = null,
	): String? = item.primaryImage?.getUrl(api, maxHeight = maxHeight)

	fun getPrimaryImageUrl(
		item: UserDto,
	): String? = item.primaryImage?.getUrl(api)

	fun getPrimaryImageUrl(
		item: BaseItemDto,
		width: Int? = null,
		height: Int? = null,
	): String? = item.itemImages[ImageType.PRIMARY]?.getUrl(
		api,
		maxWidth = width,
		maxHeight = height ?: getMaxImageHeight()
	)

	fun getPrimaryImageUrl(
		item: BaseItemDto,
		preferParentThumb: Boolean,
		fillWidth: Int? = null,
		fillHeight: Int? = null
	): String? {
		val image = when {
			preferParentThumb && item.type == BaseItemKind.EPISODE -> item.parentImages[ImageType.THUMB] ?: item.seriesThumbImage
			item.type == BaseItemKind.SEASON -> item.seriesPrimaryImage
			item.type == BaseItemKind.PROGRAM && item.imageTags?.containsKey(ImageType.THUMB) == true -> item.itemImages[ImageType.THUMB]
			item.type == BaseItemKind.AUDIO -> item.albumPrimaryImage
			else -> null
		} ?: item.itemImages[ImageType.PRIMARY]

		return image?.getUrl(
			api = api,
			fillWidth = fillWidth,
			fillHeight = fillHeight,
		)
	}

	fun getLogoImageUrl(
		item: BaseItemDto?,
		maxWidth: Int? = null
	): String? {
		val image = item?.itemImages[ImageType.LOGO] ?: item?.parentImages[ImageType.LOGO]
		return image?.getUrl(api, maxWidth = maxWidth)
	}

	fun getThumbImageUrl(
		item: BaseItemDto,
		fillWidth: Int,
		fillHeight: Int,
	): String? = item.itemImages[ImageType.THUMB]?.getUrl(api, fillWidth = fillWidth, fillHeight = fillHeight)
		?: getPrimaryImageUrl(item, true, fillWidth, fillHeight)

	fun getBannerImageUrl(item: BaseItemDto, fillWidth: Int, fillHeight: Int): String? =
		item.itemImages[ImageType.BANNER]?.getUrl(api, fillWidth = fillWidth, fillHeight = fillHeight)
			?: getPrimaryImageUrl(item, true, fillWidth, fillHeight)

	/**
	 * A utility to return a URL reference to an image resource
	 *
	 * @param resourceId The id of the image resource
	 * @return The URL of the image resource
	 */
	fun getResourceUrl(
		context: Context,
		@AnyRes resourceId: Int,
	): String = Uri.Builder()
		.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
		.authority(context.resources.getResourcePackageName(resourceId))
		.appendPath(context.resources.getResourceTypeName(resourceId))
		.appendPath(context.resources.getResourceEntryName(resourceId))
		.toString()
}
