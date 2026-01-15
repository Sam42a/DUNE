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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences

@Composable
fun ClockPreferencesScreenCompose(
    userPreferences: UserPreferences,
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
            PreferenceHeader(context.getString(R.string.pref_clock_settings))
        }

        // Empty category - ready for future clock preferences
    }
}
