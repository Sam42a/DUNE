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
import org.jellyfin.androidtv.R
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserSettingPreferences

@Composable
fun HomePreferencesScreenCompose(
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.home_prefs))
        }

        item {
            val (useExtraSmallFolders, setUseExtraSmallFolders) = rememberPreferenceState(
                preference = userSettingPreferences.useExtraSmallMediaFolders,
                preferences = userSettingPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_my_media_extra_small),
                checked = useExtraSmallFolders,
                preference = userSettingPreferences.useExtraSmallMediaFolders,
                onCheckedChange = setUseExtraSmallFolders,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            PreferenceHeader(context.getString(R.string.home_sections))
        }

        // Home sections 0-9
        val homeSections = listOf(
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
        )

        homeSections.forEachIndexed { index, sectionPref ->
            item {
                val (sectionValue, setSectionValue) = rememberEnumPreferenceState(
                    preference = sectionPref,
                    preferences = userSettingPreferences
                )
                EnumPreference(
					title = context.getString(R.string.home_section_i, index + 1),
					value = sectionValue,
					onValueChange = { newValue ->
						setSectionValue(newValue)
					},
					options = HomeSectionType.values().toList(),
					modifier = Modifier,
					description = context.getString(sectionValue.nameRes),
					optionLabel = { context.getString(it.nameRes) }
				)
            }
        }
    }
}
