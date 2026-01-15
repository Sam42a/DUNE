package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.SystemPreferences

@Composable
fun LiveTvGuideFiltersScreen(
    systemPreferences: SystemPreferences,
    onBack: () -> Unit
) {
    val filterPreferences = remember {
        listOf(
            SystemPreferences.liveTvGuideFilterMovies to R.string.lbl_movies,
            SystemPreferences.liveTvGuideFilterSeries to R.string.lbl_series,
            SystemPreferences.liveTvGuideFilterNews to R.string.lbl_news,
            SystemPreferences.liveTvGuideFilterKids to R.string.lbl_kids,
            SystemPreferences.liveTvGuideFilterSports to R.string.lbl_sports,
            SystemPreferences.liveTvGuideFilterPremiere to R.string.lbl_new_only,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PreferenceHeader(
                title = stringResource(R.string.lbl_filters),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        filterPreferences.forEach { (preference, titleRes) ->
            item {
                val (value, onValueChange) = rememberPreferenceState(preference, systemPreferences)
                
                SwitchPreference(
                    title = stringResource(titleRes),
                    checked = value,
                    onCheckedChange = onValueChange,
                    preference = preference,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
