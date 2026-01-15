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
import org.jellyfin.androidtv.preference.TelemetryPreferences

@Composable
fun CrashReportingPreferencesScreenCompose(
    telemetryPreferences: TelemetryPreferences,
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

    // Get crash report enabled state for use in multiple items
    val (crashReportEnabled, setCrashReportEnabled) = rememberPreferenceState(
        preference = TelemetryPreferences.crashReportEnabled,
        preferences = telemetryPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.pref_telemetry_category))
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_crash_reports),
                checked = crashReportEnabled,
                preference = TelemetryPreferences.crashReportEnabled,
                onCheckedChange = setCrashReportEnabled,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            val (crashReportIncludeLogs, setCrashReportIncludeLogs) = rememberPreferenceState(
                preference = TelemetryPreferences.crashReportIncludeLogs,
                preferences = telemetryPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.pref_crash_report_logs),
                checked = crashReportIncludeLogs,
                preference = TelemetryPreferences.crashReportIncludeLogs,
                onCheckedChange = setCrashReportIncludeLogs,
                enabled = crashReportEnabled
            )
        }
    }
}
