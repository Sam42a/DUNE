package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences

@Composable
fun ThemeSongPreferencesScreenCompose(
    userSettingPreferences: UserSettingPreferences,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue with screen initialization
        }
    }

    // Get preference states
    val (themeSongsEnabled, setThemeSongsEnabled) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsEnabled,
        preferences = userSettingPreferences
    )

    val (themeSongsMovies, setThemeSongsMovies) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsMovies,
        preferences = userSettingPreferences
    )

    val (themeSongsSeries, setThemeSongsSeries) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsSeries,
        preferences = userSettingPreferences
    )

    val (themeSongsEpisodes, setThemeSongsEpisodes) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsEpisodes,
        preferences = userSettingPreferences
    )

    val (themeSongsArchiveFallback, setThemeSongsArchiveFallback) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsArchiveFallback,
        preferences = userSettingPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.pref_theme_song_media_types))
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_theme_song_movies),
                checked = themeSongsMovies,
                preference = userSettingPreferences.themeSongsMovies,
                onCheckedChange = setThemeSongsMovies,
                enabled = themeSongsEnabled,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_theme_song_series),
                checked = themeSongsSeries,
                preference = userSettingPreferences.themeSongsSeries,
                onCheckedChange = setThemeSongsSeries,
                enabled = themeSongsEnabled
            )
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_theme_song_episodes),
                checked = themeSongsEpisodes,
                preference = userSettingPreferences.themeSongsEpisodes,
                onCheckedChange = setThemeSongsEpisodes,
                enabled = themeSongsEnabled
            )
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_theme_song_archive_fallback),
                description = context.getString(R.string.pref_theme_song_archive_fallback_summary),
                checked = themeSongsArchiveFallback,
                preference = userSettingPreferences.themeSongsArchiveFallback,
                onCheckedChange = setThemeSongsArchiveFallback,
                enabled = themeSongsEnabled
            )
        }
    }
}
