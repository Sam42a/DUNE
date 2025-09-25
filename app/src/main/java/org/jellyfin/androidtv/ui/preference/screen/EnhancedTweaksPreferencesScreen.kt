package org.jellyfin.androidtv.ui.preference.screen

import android.R.attr.icon
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.preference.constant.GenreSortBy
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.enum
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.koin.android.ext.android.inject

class EnhancedTweaksPreferencesScreen : OptionsFragment() {
    private val userPreferences: UserPreferences by inject()
    private val userSettingPreferences: UserSettingPreferences by inject()

    override val screen by optionsScreen {
        setTitle(R.string.enhanced_tweaks)

        category {

            link {
                setTitle(R.string.backdrop_settings)
                setContent(R.string.backdrop_settings_description)
                icon = R.drawable.ic_backdrop
                withFragment<BackdropSettingsPreferencesScreen>()
            }

            enum<AppTheme> {
                setTitle(R.string.pref_app_theme)
				bind(userPreferences, UserPreferences.appTheme)
            }

            checkbox {
                setTitle(R.string.show_live_tv_button)
                setContent(R.string.show_live_tv_button_summary)
                bind(userSettingPreferences, userSettingPreferences.showLiveTvButton)
            }

            checkbox {
                setTitle(R.string.show_random_button)
                setContent(R.string.show_random_button_summary)
                bind(userSettingPreferences, userSettingPreferences.showRandomButton)
            }

            checkbox {
                setTitle(R.string.lbl_use_series_thumbnails)
                setContent(R.string.lbl_use_series_thumbnails_description)
                bind(userPreferences, UserPreferences.seriesThumbnailsEnabled)
            }
        }

        category {
            setTitle(R.string.genre_rows)

			// Genre sorting method
			enum<GenreSortBy> {
				setTitle(R.string.pref_genre_sort_by)
				bind(userPreferences, UserPreferences.genreSortBy)
			}

			// Music Videos
            checkbox {
                setTitle(R.string.show_music_videos_row)
                bind(userSettingPreferences, userSettingPreferences.showMusicVideosRow)
            }

            // Collections
            checkbox {
                setTitle(R.string.show_collections_row)
                bind(userSettingPreferences, userSettingPreferences.showCollectionsRow)
            }

            // Genre rows in specified order
            checkbox {
                setTitle(R.string.show_sci_fi_row)
                bind(userSettingPreferences, userSettingPreferences.showSciFiRow)
            }
            checkbox {
                setTitle(R.string.show_comedy_row)
                bind(userSettingPreferences, userSettingPreferences.showComedyRow)
            }
            checkbox {
                setTitle(R.string.show_romance_row)
                bind(userSettingPreferences, userSettingPreferences.showRomanceRow)
            }
            checkbox {
                setTitle(R.string.show_anime_row)
                bind(userSettingPreferences, userSettingPreferences.showAnimeRow)
            }
            checkbox {
                setTitle(R.string.show_action_row)
                bind(userSettingPreferences, userSettingPreferences.showActionRow)
            }
            checkbox {
                setTitle(R.string.genre_row_action_adventure)
                bind(userSettingPreferences, userSettingPreferences.showActionAdventureRow)
            }
            checkbox {
                setTitle(R.string.show_documentary_row)
                bind(userSettingPreferences, userSettingPreferences.showDocumentaryRow)
            }
            checkbox {
                setTitle(R.string.show_reality_row)
                bind(userSettingPreferences, userSettingPreferences.showRealityRow)
            }
            checkbox {
                setTitle(R.string.show_family_row)
                bind(userSettingPreferences, userSettingPreferences.showFamilyRow)
            }
            checkbox {
                setTitle(R.string.show_horror_row)
                bind(userSettingPreferences, userSettingPreferences.showHorrorRow)
            }
            checkbox {
                setTitle(R.string.show_fantasy_row)
                bind(userSettingPreferences, userSettingPreferences.showFantasyRow)
            }
            checkbox {
                setTitle(R.string.show_history_row)
                bind(userSettingPreferences, userSettingPreferences.showHistoryRow)
            }
            checkbox {
                setTitle(R.string.show_music_row)
                bind(userSettingPreferences, userSettingPreferences.showMusicRow)
            }
            checkbox {
                setTitle(R.string.show_mystery_row)
                bind(userSettingPreferences, userSettingPreferences.showMysteryRow)
            }
            checkbox {
                setTitle(R.string.show_thriller_row)
                bind(userSettingPreferences, userSettingPreferences.showThrillerRow)
            }
            checkbox {
                setTitle(R.string.show_war_row)
                bind(userSettingPreferences, userSettingPreferences.showWarRow)
            }
        }
    }
}
