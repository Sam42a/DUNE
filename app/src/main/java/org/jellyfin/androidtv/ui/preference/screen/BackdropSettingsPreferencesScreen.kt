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
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences

@Composable
fun BackdropSettingsPreferencesScreenCompose(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
        }
    }

    val (backdropEnabled, setBackdropEnabled) = rememberPreferenceState(
        preference = UserPreferences.backdropEnabled,
        preferences = userPreferences
    )

    val (backdropFadingIntensity, setBackdropFadingIntensity) = rememberPreferenceState(
        preference = UserPreferences.backdropFadingIntensity,
        preferences = userPreferences
    )

    val (backdropDynamicColors, setBackdropDynamicColors) = rememberPreferenceState(
        preference = UserPreferences.backdropDynamicColors,
        preferences = userPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.backdrop_settings))
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.lbl_show_backdrop),
                description = context.getString(R.string.pref_show_backdrop_description),
                checked = backdropEnabled,
                preference = UserPreferences.backdropEnabled,
                onCheckedChange = setBackdropEnabled,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.Dynamic_colors),
                description = context.getString(R.string.pref_dynamic_colors),
                checked = backdropDynamicColors,
                preference = UserPreferences.backdropDynamicColors,
                onCheckedChange = setBackdropDynamicColors
            )
        }

        item {
            ListPreference(
                title = context.getString(R.string.lbl_backdrop_fading),
                value = backdropFadingIntensity.toString(),
                onValueChange = { newValue ->
                    setBackdropFadingIntensity(newValue.toFloat())
                },
                options = mapOf(
                    "0.5" to context.getString(R.string.lbl_fading_effect_low),
                    "0.6" to context.getString(R.string.lbl_fading_effect_Medium),
                    "0.7" to context.getString(R.string.lbl_fading_effect_High),
                ),
                defaultValue = UserPreferences.backdropFadingIntensity.defaultValue.toString()
            )
        }
    }
}
