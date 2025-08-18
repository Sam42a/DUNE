package org.jellyfin.androidtv.util

import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Getter to get the style resource for a given theme.
 */
private val AppTheme.style
	get() = when (this) {
		AppTheme.PURPLE_HAZE -> R.style.Theme_Jellyfin_PurpleHaze
		AppTheme.DARK -> R.style.Theme_Jellyfin
		AppTheme.EMERALD -> R.style.Theme_Jellyfin_Emerald
		AppTheme.MUTED_PURPLE -> R.style.Theme_Jellyfin_MutedPurple
		AppTheme.BASIC -> R.style.Theme_Basic
		AppTheme.FLEXY -> R.style.Theme_Jellyfin_Flexy
		AppTheme.YELLOW_TOWN -> R.style.Theme_YellowTown
		AppTheme.DARK_PURPLE -> R.style.Theme_Jellyfin_DarkPurple

	}

/**
 * Private view model for the [applyTheme] extension to store the currently set theme.
 */
class ThemeViewModel : ViewModel() {
	var theme: AppTheme? = null
}

/**
 * Extension function to set the theme. Should be called in [FragmentActivity.onCreate] and
 * [FragmentActivity.onResume]. It recreates the activity when the theme changed after it was set.
 * Do not call during resume if the activity may not be recreated (like in the video player).
 */
fun FragmentActivity.applyTheme() {
    val viewModel by viewModels<ThemeViewModel>()
    val userPreferences by inject<UserPreferences>()
    val newTheme = userPreferences[UserPreferences.appTheme]

    // Always set the theme, but only recreate if it's changed
    if (newTheme != viewModel.theme) {
        Timber.i("Theme changed from ${viewModel.theme} to $newTheme, applying...")
        viewModel.theme = newTheme
        setTheme(newTheme.style)

        // Only recreate if we're not in the middle of creating the activity
        if (!isFinishing && !isDestroyed) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    recreate()
                }
            }
        }
    } else {
        // Just apply the theme without recreation if it's already set
        setTheme(newTheme.style)
    }
}
