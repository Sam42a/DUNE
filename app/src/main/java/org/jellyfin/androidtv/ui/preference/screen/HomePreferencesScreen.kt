package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.enum
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.preference.store.PreferenceStore
import org.koin.android.ext.android.inject

class HomePreferencesScreen : OptionsFragment() {
	private val userSettingPreferences: UserSettingPreferences by inject()

	override val screen by optionsScreen {
		setTitle(R.string.home_prefs)

		category {
			setTitle(R.string.customization)

			checkbox {
				setTitle(R.string.lbl_my_media_extra_small)
				setContent(R.string.desc_my_media_extra_small)
				bind(userSettingPreferences, userSettingPreferences.useExtraSmallMediaFolders)
			}
		}

		category {
			setTitle(R.string.home_sections)

			listOf(
				userSettingPreferences.homesection0,
				userSettingPreferences.homesection1,
				userSettingPreferences.homesection2,
				userSettingPreferences.homesection3,
				userSettingPreferences.homesection4,
				userSettingPreferences.homesection5,
				userSettingPreferences.homesection6,
				userSettingPreferences.homesection7,
				userSettingPreferences.homesection8,
				userSettingPreferences.homesection9
			).forEachIndexed { index, sectionPref ->
				enum<HomeSectionType> {
					title = getString(R.string.home_section_i, index + 1)
					bind(userSettingPreferences, sectionPref)
				}
			}
		}



	}

	override val stores: Array<PreferenceStore<*, *>>
		get() = arrayOf(userSettingPreferences)
}