package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.koin.android.ext.android.inject
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen

class ThemeSongPreferencesScreen : OptionsFragment() {
    private val userSettingPreferences: UserSettingPreferences by inject()

    override val screen by optionsScreen {
        setTitle(R.string.pref_theme_song_media_types)

        category {

            checkbox {
                setTitle(R.string.pref_theme_song_movies)
                bind(userSettingPreferences, userSettingPreferences.themeSongsMovies)
                depends { userSettingPreferences[userSettingPreferences.themeSongsEnabled] }
            }

            checkbox {
                setTitle(R.string.pref_theme_song_series)
                bind(userSettingPreferences, userSettingPreferences.themeSongsSeries)
                depends { userSettingPreferences[userSettingPreferences.themeSongsEnabled] }
            }

            checkbox {
                setTitle(R.string.pref_theme_song_episodes)
                bind(userSettingPreferences, userSettingPreferences.themeSongsEpisodes)
                depends { userSettingPreferences[userSettingPreferences.themeSongsEnabled] }
            }

            checkbox {
                setTitle(R.string.pref_theme_song_archive_fallback)
				setContent(R.string.pref_theme_song_archive_fallback_summary)
				bind(userSettingPreferences, userSettingPreferences.themeSongsArchiveFallback)
                depends { userSettingPreferences[userSettingPreferences.themeSongsEnabled] }
            }
        }
    }
}
