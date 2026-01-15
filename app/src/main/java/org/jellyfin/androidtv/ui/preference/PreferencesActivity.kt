package org.jellyfin.androidtv.ui.preference

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import org.jellyfin.androidtv.ui.preference.screen.PreferencesRoot
import org.jellyfin.androidtv.ui.preference.screen.UserPreferencesScreenCompose
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.data.service.BackgroundService
import org.koin.android.ext.android.inject
import coil3.ImageLoader

class PreferencesComposeActivity : ComponentActivity() {

    private val userPreferences: UserPreferences by inject()
    private val userSettingPreferences: UserSettingPreferences by inject()
    private val systemPreferences: SystemPreferences by inject()
    private val telemetryPreferences: TelemetryPreferences by inject()
    private val imageLoader: ImageLoader by inject()
    private val backgroundService: BackgroundService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialScreen = intent.getStringExtra("initialScreen") ?: "main"
        val displayPreferencesId = intent.getStringExtra("displayPreferencesId") ?: ""
        val allowViewSelection = intent.getBooleanExtra("allowViewSelection", true)
        val isStandalone = intent.getBooleanExtra("standalone", false)
        val shouldRefresh = intent.getBooleanExtra("shouldRefresh", false)
        val serverId = intent.getStringExtra("serverId") ?: ""
        val showAbout = intent.getBooleanExtra("showAbout", false)

        setContent {
            MaterialTheme {
                PreferencesRoot {
                    UserPreferencesScreenCompose(
                        userPreferences = userPreferences,
                        userSettingPreferences = userSettingPreferences,
                        systemPreferences = systemPreferences,
                        telemetryPreferences = telemetryPreferences,
                        imageLoader = imageLoader,
                        backgroundService = backgroundService,
                        initialScreen = initialScreen,
                        initialDisplayPreferencesId = displayPreferencesId,
                        initialAllowViewSelection = allowViewSelection,
                        isStandalone = isStandalone,
                        shouldRefresh = shouldRefresh,
                        initialServerId = serverId,
                        showAbout = showAbout,
                        onExit = {
                            if (isStandalone && shouldRefresh) {
                                setResult(android.app.Activity.RESULT_OK)
                                finish()
                            } else {
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
	override fun onBackPressed() {
        // Let the Compose screen handle all back navigation
        // The Compose screen will call finish() when appropriate
    }
}

