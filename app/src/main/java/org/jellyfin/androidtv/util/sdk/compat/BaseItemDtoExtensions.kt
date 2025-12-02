package org.jellyfin.androidtv.util.sdk.compat

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo

/**
 * Extension functions for BaseItemDto that are callable from Java
 */
object BaseItemDtoExtensions {
    @JvmStatic
    fun copyWithMediaSources(
        item: BaseItemDto,
        mediaSources: List<MediaSourceInfo>?
    ): BaseItemDto = item.copy(
        mediaSources = mediaSources
    )
}
