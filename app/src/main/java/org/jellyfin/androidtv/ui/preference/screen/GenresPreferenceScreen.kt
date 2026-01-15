package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.GenreRowType
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences

@Composable
fun GenresPreferenceScreenCompose(
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
            // Focus request failed, but continue with screen initialization
        }
    }

    // Get preference states
    val (genreItemLimit, setGenreItemLimit) = rememberPreferenceState(
        preference = UserPreferences.genreItemLimit,
        preferences = userPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.home_custom_sections))
        }

        item {
            SeekBarPreference(
                title = context.getString(R.string.genre_item_limit),
                value = genreItemLimit,
                range = 5..35,
                onValueChange = setGenreItemLimit,
                modifier = Modifier.fillMaxWidth(),
                step = 1
            )
        }

		// Home sections 0-9
		val genreRowPreferences = listOf(
			userSettingPreferences.genrerow0,
			userSettingPreferences.genrerow1,
			userSettingPreferences.genrerow2,
			userSettingPreferences.genrerow3,
			userSettingPreferences.genrerow4,
			userSettingPreferences.genrerow5,
			userSettingPreferences.genrerow6,
			userSettingPreferences.genrerow7,
			userSettingPreferences.genrerow8,
			userSettingPreferences.genrerow9
		)

		genreRowPreferences.forEachIndexed { index, sectionPref ->
			item {
				val (sectionValue, setSectionValue) = rememberEnumPreferenceState(
					preference = sectionPref,
					preferences = userSettingPreferences
				)
				EnumPreference(
					title = context.getString(R.string.home_section_i, index + 1),
					value = sectionValue,
					onValueChange = setSectionValue,
					options = GenreRowType.entries,
					modifier = Modifier,
					description = context.getString(sectionValue.nameRes),
					optionLabel = { context.getString(it.nameRes) }
				)
			}
		}



		}
    }


