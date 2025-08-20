package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen

class ClockPreferencesScreen(private val userPreferences: UserPreferences) : OptionsFragment() {

	override val screen by optionsScreen {
		setTitle(R.string.pref_clock_settings)

		category {
		}
	}
}
