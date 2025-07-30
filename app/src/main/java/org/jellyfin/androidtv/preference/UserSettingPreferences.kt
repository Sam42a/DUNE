package org.jellyfin.androidtv.preference

import android.content.Context
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.store.SharedPreferenceStore

class UserSettingPreferences(context: Context) : SharedPreferenceStore(
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
) {
	    val showComedyRow = booleanPreference("showComedyRow", false)
    val showRomanceRow = booleanPreference("showRomanceRow", false)
    val showAnimeRow = booleanPreference("showAnimeRow", false)
    val showAnimationRow = booleanPreference("showAnimationRow", false)
    val showActionRow = booleanPreference("showActionRow", false)
    val showActionAdventureRow = booleanPreference("showActionAdventureRow", false)
    val showSciFiRow = booleanPreference("showSciFiRow", false)
    val showDocumentaryRow = booleanPreference("showDocumentaryRow", false)
    val showDramaRow = booleanPreference("showDramaRow", false)
    val showRealityTvRow = booleanPreference("showRealityTvRow", false)
    val showFamilyRow = booleanPreference("showFamilyRow", false)
    val showHorrorRow = booleanPreference("showHorrorRow", false)
    val showFantasyRow = booleanPreference("showFantasyRow", false)
    val showHistoryRow = booleanPreference("showHistoryRow", false)
    val showMusicRow = booleanPreference("showMusicRow", false)
    val showMysteryRow = booleanPreference("showMysteryRow", false)
    val showRealityRow = booleanPreference("showRealityRow", false)
    val showThrillerRow = booleanPreference("showThrillerRow", false)
    val showWarRow = booleanPreference("showWarRow", false)
    val showMusicVideosRow = booleanPreference("showMusicVideosRow", false)

    private val defaultGenreOrder = listOf(
        "Comedy",
        "Romance",
        "Anime",
        "Animation",
        "Action",
        "Sci-Fi",
        "Documentary",
        "Drama",
        "Family",
        "Horror",
        "Fantasy",
        "History",
        "Music",
        "Mystery",
        "Reality",
        "Thriller",
        "War"
    )
    private val genreRowOrderKey = "genreRowOrder"

    @JvmField
    val skipBackLength = intPreference("skipBackLength", 10_000)
    @JvmField
    val skipForwardLength = intPreference("skipForwardLength", 30_000)

    // Media folder display options
    val useExtraSmallMediaFolders = booleanPreference("useExtraSmallMediaFolders", true)
    val showLiveTvButton = booleanPreference("show_live_tv_button", false)
    val showRandomButton = booleanPreference("show_masks_button", false)

    val homesection0 = enumPreference("homesection0", HomeSectionType.LIBRARY_TILES_SMALL)
    val homesection1 = enumPreference("homesection1", HomeSectionType.RESUME)
    val homesection2 = enumPreference("homesection2", HomeSectionType.NEXT_UP)
    val homesection3 = enumPreference("homesection3", HomeSectionType.LATEST_MEDIA)
    val homesection4 = enumPreference("homesection4", HomeSectionType.NONE)
    val homesection5 = enumPreference("homesection5", HomeSectionType.NONE)
    val homesection6 = enumPreference("homesection6", HomeSectionType.NONE)
    val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
    val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
    val homesection9 = enumPreference("homesection9", HomeSectionType.NONE)

    fun getGenreRowOrder(): List<String> {
        val raw = getString(genreRowOrderKey, defaultGenreOrder.joinToString(","))
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun setGenreRowOrder(order: List<String>) {
        setString(genreRowOrderKey, order.joinToString(","))
    }
}
