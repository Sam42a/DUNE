package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.koin.android.ext.android.inject
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.list
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen

class BackdropSettingsPreferencesScreen : OptionsFragment() {
    private val userPreferences: UserPreferences by inject()
    private val userSettingPreferences: UserSettingPreferences by inject()

    override val screen by optionsScreen {
        setTitle(R.string.backdrop_settings)


        category {
            setTitle(R.string.backdrop_settings)


            checkbox {
                setTitle(R.string.lbl_show_backdrop)
                setContent(R.string.pref_show_backdrop_description)
                bind(userPreferences, UserPreferences.backdropEnabled)
            }


            list {
                setTitle(R.string.lbl_backdrop_fading)
                entries = mapOf(
                    "0.0" to "0%",
                    "0.1" to "10%",
                    "0.2" to "20%",
                    "0.3" to "30%",
                    "0.4" to "40%",
                    "0.5" to "50%",
                    "0.6" to "60%",
                    "0.7" to "70%",
                    "0.8" to "80%",
                    "0.9" to "90%",
                    "1.0" to "100%"
                )
                bind {
                    get { userPreferences[UserPreferences.backdropFadingIntensity].toString() }
                    set { value -> userPreferences[UserPreferences.backdropFadingIntensity] = value.toFloat() }
                    default { UserPreferences.backdropFadingIntensity.defaultValue.toString() }
                }
            }


list {
                setTitle(R.string.lbl_backdrop_dimming)
                entries = mapOf(
                    "0.0" to "0%",
                    "0.1" to "10%",
                    "0.2" to "20%",
                    "0.3" to "30%",
                    "0.4" to "40%",
                    "0.5" to "50%",
                    "0.6" to "60%",
                    "0.7" to "70%",
                    "0.8" to "80%",
                    "0.9" to "90%",
                    "1.0" to "100%"
                )
                bind {
                    get { userPreferences[UserPreferences.backdropDimmingIntensity].toString() }
                    set { value -> userPreferences[UserPreferences.backdropDimmingIntensity] = value.toFloat() }
                    default { UserPreferences.backdropDimmingIntensity.defaultValue.toString() }
                }
            }


            list {
                setTitle(R.string.image_quality)
                entries = mapOf(
                    "low" to getString(R.string.image_quality_low),
                    "normal" to getString(R.string.image_quality_normal),
                    "high" to getString(R.string.image_quality_high)
                )
                bind {
                    get { userPreferences[UserPreferences.imageQuality] }
                    set { value -> userPreferences[UserPreferences.imageQuality] = value }
                    default { UserPreferences.imageQuality.defaultValue }
                }
            }
        }
    }
}
