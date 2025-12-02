package org.jellyfin.androidtv.ui.home.carousel

import org.jellyfin.sdk.model.api.BaseItemDto

data class CarouselItem(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val backdropUrl: String? = null,
    val runtimeTicks: Long? = null,
    val productionYear: Int? = null,
    val communityRating: Float? = null,
    val criticRating: Float? = null,
    val playCount: Int? = null,
    val isFolder: Boolean = false,
    val type: String? = null
) {
    companion object {
        fun fromBaseItemDto(
            item: BaseItemDto,
            imageHelper: org.jellyfin.androidtv.util.ImageHelper,
            api: org.jellyfin.sdk.api.client.ApiClient
        ): CarouselItem? {
            if (item.id == null) return null

            val backdropUrl = imageHelper.getBackdropImageUrl(item, maxWidth = 1920, maxHeight = 1080)
            val thumbImageUrl = imageHelper.getThumbImageUrl(item, fillWidth = 1920, fillHeight = 720) ?: ""

            timber.log.Timber.d("Item ${item.name} - Backdrop URL: ${backdropUrl ?: "NULL"}, Thumb URL: $thumbImageUrl")

            return CarouselItem(
                id = item.id.toString(),
                title = item.name ?: "",
                description = item.overview ?: "",
                imageUrl = backdropUrl ?: thumbImageUrl,
                backdropUrl = backdropUrl ?: thumbImageUrl,
                runtimeTicks = item.runTimeTicks,
                productionYear = item.productionYear,
                communityRating = item.communityRating,
                criticRating = item.criticRating,
                playCount = item.userData?.playCount,
                isFolder = item.isFolder ?: false,
                type = item.type?.name
            ).also { carouselItem ->
                timber.log.Timber.d("Created carousel item: ${carouselItem.title} - Description: ${carouselItem.description.take(50)}...")
            }
        }
    }

    fun getRuntime(): String {
        return runtimeTicks?.let { ticks ->
            val minutes = ticks / 600000000L
            val hours = minutes / 60
            val remainingMinutes = minutes % 60

            when {
                hours > 0 -> "${hours}h ${remainingMinutes}m"
                minutes > 0 -> "${minutes}m"
                else -> ""
            }
        } ?: ""
    }

    fun getYear(): String = productionYear?.toString() ?: ""
    fun getRatingText(): String {
        return when {
            communityRating != null && communityRating > 0 -> String.format("%.1f", communityRating)
            criticRating != null && criticRating > 0 -> String.format("%.1f", criticRating)
            else -> ""
        }
    }
}
