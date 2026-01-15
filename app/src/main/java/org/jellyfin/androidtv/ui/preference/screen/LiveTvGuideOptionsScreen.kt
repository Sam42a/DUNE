package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.LiveTvPreferences
import org.jellyfin.androidtv.preference.constant.LiveTvChannelOrder

@Composable
fun LiveTvGuideOptionsScreen(
    liveTvPreferences: LiveTvPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val channelOrderOptions = remember { LiveTvChannelOrder.entries }
    
    val indicatorPreferences = remember {
        listOf(
            LiveTvPreferences.showHDIndicator to R.string.lbl_hd_programs,
            LiveTvPreferences.showLiveIndicator to R.string.lbl_live_broadcasts,
            LiveTvPreferences.showNewIndicator to R.string.lbl_new_episodes,
            LiveTvPreferences.showPremiereIndicator to R.string.lbl_premieres,
            LiveTvPreferences.showRepeatIndicator to R.string.lbl_repeat_episodes,
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
                title = stringResource(R.string.live_tv_preferences),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item {
            val initialValue = remember { LiveTvChannelOrder.fromString(liveTvPreferences[LiveTvPreferences.channelOrder]) }
            val (currentChannelOrder, setCurrentChannelOrder) = remember { mutableStateOf(initialValue) }
            
            LaunchedEffect(currentChannelOrder) {
                liveTvPreferences[LiveTvPreferences.channelOrder] = currentChannelOrder.stringValue
            }
            
            EnumPreference(
                title = stringResource(R.string.lbl_sort_by),
                value = currentChannelOrder,
                onValueChange = setCurrentChannelOrder,
                options = channelOrderOptions,
                optionLabel = { context.getString(it.nameRes) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val (favsAtTop, onFavsAtTopChange) = rememberPreferenceState(
                LiveTvPreferences.favsAtTop,
                liveTvPreferences
            )
            SwitchPreference(
                title = stringResource(R.string.lbl_start_favorites),
                checked = favsAtTop,
                onCheckedChange = onFavsAtTopChange,
                preference = LiveTvPreferences.favsAtTop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val (colorCodeGuide, onColorCodeGuideChange) = rememberPreferenceState(
                LiveTvPreferences.colorCodeGuide,
                liveTvPreferences
            )
            SwitchPreference(
                title = stringResource(R.string.lbl_colored_backgrounds),
                checked = colorCodeGuide,
                onCheckedChange = onColorCodeGuideChange,
                preference = LiveTvPreferences.colorCodeGuide,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            PreferenceHeader(
                title = stringResource(R.string.lbl_show_indicators),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        indicatorPreferences.forEach { (preference, titleRes) ->
            item {
                val (value, onValueChange) = rememberPreferenceState(preference, liveTvPreferences)
                
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
