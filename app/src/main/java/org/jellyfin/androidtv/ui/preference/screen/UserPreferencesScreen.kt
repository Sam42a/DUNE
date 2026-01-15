package org.jellyfin.androidtv.ui.preference.screen

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.LiveTvPreferences
import org.jellyfin.androidtv.ui.startup.preference.compose.EditServerScreenCompose
import org.jellyfin.androidtv.ui.startup.preference.compose.EditUserScreenCompose
import org.jellyfin.androidtv.util.AppUpdater
import org.jellyfin.androidtv.util.UpdateResult
import org.koin.java.KoinJavaComponent
import timber.log.Timber

private const val CURRENT_VERSION = "0.1.1"

private fun checkForUpdates(context: Context) {
    val appUpdater = AppUpdater(context)

    // Show checking message
    Toast.makeText(context, context.getString(R.string.update_checking), Toast.LENGTH_SHORT).show()

    CoroutineScope(Dispatchers.Main).launch {
        val result = withContext(Dispatchers.IO) {
            try {
                appUpdater.checkForUpdates(CURRENT_VERSION)
            } catch (e: Exception) {
                UpdateResult.Error(e.message ?: "Unknown error")
            }
        }

        when (result) {
            is UpdateResult.UpdateAvailable -> {
                // Show update available message
                Toast.makeText(
                    context,
                    context.getString(R.string.update_available_message, result.version),
                    Toast.LENGTH_LONG
                ).show()

                // Start download and install in a coroutine
                Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
                Timber.tag("UpdateCheck").d("Starting download for version ${result.version}")
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        Timber.tag("UpdateCheck").d("Launching download coroutine")
                        withContext(Dispatchers.IO) {
                            try {
                                Timber.tag("UpdateCheck").d("Calling downloadAndInstall")
                                appUpdater.downloadAndInstall(result.version, result.downloadUrl)
                                Timber.tag("UpdateCheck").d("downloadAndInstall completed")
                            } catch (e: Exception) {
                                Timber.tag("UpdateCheck").e(e, "Error in downloadAndInstall")
                                throw e
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("UpdateCheck").e(e, "Error in coroutine")
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.update_download_failed)}: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            is UpdateResult.NoUpdateAvailable -> {
                Toast.makeText(
                    context,
                    R.string.update_no_updates,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is UpdateResult.Error -> {
                Toast.makeText(
                    context,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@Composable
fun UserPreferencesScreenCompose(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    systemPreferences: SystemPreferences,
    telemetryPreferences: TelemetryPreferences,
    imageLoader: ImageLoader,
    backgroundService: BackgroundService,
    initialScreen: String = "main",
    initialDisplayPreferencesId: String = "",
    initialAllowViewSelection: Boolean = true,
    isStandalone: Boolean = false,
    shouldRefresh: Boolean = false,
    initialServerId: String = "",
    showAbout: Boolean = false,
    onExit: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var currentDisplayPreferencesId by remember { mutableStateOf(initialDisplayPreferencesId) }
    var currentAllowViewSelection by remember { mutableStateOf(initialAllowViewSelection) }
    var currentServerId by remember { mutableStateOf(initialServerId) }
    var currentUserId by remember { mutableStateOf("") }

    // Handle back navigation
    fun handleBackPress() {
        when (currentScreen) {
            "main" -> onExit()
            "about" -> currentScreen = "main"
            "licenses" -> currentScreen = "main"
            "customization" -> currentScreen = "main"
            "backdrop_settings" -> currentScreen = "customization"
            "theme_songs" -> currentScreen = "customization"
            "genres" -> currentScreen = "customization"
            "libraries" -> currentScreen = "customization"
            "display_preferences" -> {
                if (isStandalone) {
                    onExit() // Exit directly in standalone mode
                } else {
                    currentScreen = "libraries" // Normal navigation
                }
            }
            "auth" -> {
                if (isStandalone) {
                    onExit() // Exit directly in standalone startup mode
                } else {
                    currentScreen = "main" // Normal navigation back to main
                }
            }
            "edit_server" -> currentScreen = "auth"
            "edit_user" -> currentScreen = "edit_server"
            "developer" -> currentScreen = "main"
            "home" -> currentScreen = "customization"
            "crash_reporting" -> currentScreen = "main"
            "playback" -> currentScreen = "main"
            "playback_advanced" -> currentScreen = "playback"
            "live_tv_guide_filters" -> onExit() // Exit directly in standalone mode
            "live_tv_guide_options" -> onExit() // Exit directly in standalone mode
            else -> onExit()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val backgroundService: BackgroundService = KoinJavaComponent.get(BackgroundService::class.java)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Backdrop blocking is now handled at activity level to prevent image flashes
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    handleBackPress()
                    true
                } else {
                    false
                }
            }
    ) {
        when (currentScreen) {
            "main" -> MainPreferencesScreen(userPreferences, userSettingPreferences, systemPreferences, imageLoader, onNavigate = { screen ->
                currentScreen = screen
            }, onExit = onExit)
            "about" -> AboutScreenCompose(
                onNavigateToLicenses = { currentScreen = "licenses" },
                onBack = { currentScreen = "main" }
            )
            "licenses" -> LicensesScreenCompose(
                onBack = { currentScreen = "main" }
            )
            "auth" -> {
                val context = LocalContext.current
                AuthPreferencesScreenCompose(
                    onBack = {
                        if (isStandalone) {
                            onExit() // Exit directly in standalone startup mode
                        } else {
                            currentScreen = "main" // Normal navigation back to main
                        }
                    },
                    serverRepository = KoinJavaComponent.get(ServerRepository::class.java),
                    serverUserRepository = KoinJavaComponent.get(ServerUserRepository::class.java),
                    authenticationPreferences = KoinJavaComponent.get(AuthenticationPreferences::class.java),
                    sessionRepository = KoinJavaComponent.get(SessionRepository::class.java),
                    showAbout = showAbout,
                    onNavigateToEditServer = { serverId ->
                        // Navigate to EditServerScreenCompose via PreferencesComposeActivity
                        val intent = android.content.Intent(
                            context,
                            org.jellyfin.androidtv.ui.preference.PreferencesComposeActivity::class.java
                        ).apply {
                            putExtra("initialScreen", "edit_server")
                            putExtra("serverId", serverId)
                            putExtra("standalone", true)
                        }
                        context.startActivity(intent)
                    },
                    onNavigateToLicenses = { currentScreen = "licenses" }
                )
            }
            "customization" -> CustomizationPreferencesScreenCompose(
                userPreferences = userPreferences,
                userSettingPreferences = userSettingPreferences,
                onBack = { currentScreen = "main" },
                onNavigateToHome = { currentScreen = "home" },
                onNavigateToLibraries = { currentScreen = "libraries" },
                onNavigateToBackdropSettings = { currentScreen = "backdrop_settings" },
                onNavigateToThemeSongs = { currentScreen = "theme_songs" },
                onNavigateToGenres = { currentScreen = "genres" }
            )
            "backdrop_settings" -> BackdropSettingsPreferencesScreenCompose(
                userPreferences = userPreferences,
                userSettingPreferences = userSettingPreferences,
                onBack = { currentScreen = "customization" }
            )
            "theme_songs" -> ThemeSongPreferencesScreenCompose(
                userSettingPreferences = userSettingPreferences,
                onBack = { currentScreen = "customization" }
            )
            "genres" -> GenresPreferenceScreenCompose(
                userPreferences = userPreferences,
                userSettingPreferences = userSettingPreferences,
                onBack = { currentScreen = "customization" }
            )
            "libraries" -> LibrariesPreferencesScreenCompose(
                onBack = { currentScreen = "customization" },
                onNavigateToDisplayPreferences = { preferencesId, allowViewSelection ->
                    currentDisplayPreferencesId = preferencesId
                    currentAllowViewSelection = allowViewSelection
                    currentScreen = "display_preferences"
                },
                userViewsRepository = KoinJavaComponent.inject<UserViewsRepository>(UserViewsRepository::class.java).value
            )
            "display_preferences" -> DisplayPreferencesScreenCompose(
                preferencesId = currentDisplayPreferencesId,
                allowViewSelection = currentAllowViewSelection,
                onBack = {
                    if (isStandalone) {
                        onExit() // Exit directly in standalone mode
                    } else {
                        currentScreen = "libraries" // Normal navigation
                    }
                },
                preferencesRepository = KoinJavaComponent.inject<org.jellyfin.androidtv.preference.PreferencesRepository>(org.jellyfin.androidtv.preference.PreferencesRepository::class.java).value
            )
            "developer" -> DeveloperPreferencesScreenCompose(userPreferences, systemPreferences, imageLoader) {
                currentScreen = "main"
            }
            "home" -> HomePreferencesScreenCompose(userSettingPreferences) {
                currentScreen = "customization"
            }
            "crash_reporting" -> CrashReportingPreferencesScreenCompose(
                telemetryPreferences = telemetryPreferences,
                onBack = { currentScreen = "main" }
            )
            "playback" -> PlaybackPreferencesScreenCompose(
                userPreferences = userPreferences,
                userSettingPreferences = userSettingPreferences,
                mediaSegmentRepository = KoinJavaComponent.get(org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository::class.java),
                onBack = { currentScreen = "main" },
                onNavigateToAdvanced = { currentScreen = "playback_advanced" }
            )
            "playback_advanced" -> PlaybackAdvancedPreferencesScreenCompose(
                userPreferences = userPreferences,
                onBack = { currentScreen = "playback" }
            )
            "live_tv_guide_filters" -> LiveTvGuideFiltersScreen(
                systemPreferences = systemPreferences,
                onBack = { onExit() } // Exit directly in standalone mode
            )
            "live_tv_guide_options" -> LiveTvGuideOptionsScreen(
                liveTvPreferences = KoinJavaComponent.get<LiveTvPreferences>(LiveTvPreferences::class.java),
                onBack = { onExit() } // Exit directly in standalone mode
            )
            "edit_server" -> {
                val serverId = currentServerId
                val startupViewModel = KoinJavaComponent.get<org.jellyfin.androidtv.ui.startup.StartupViewModel>(org.jellyfin.androidtv.ui.startup.StartupViewModel::class.java)
                val serverUserRepository = KoinJavaComponent.get<ServerUserRepository>(ServerUserRepository::class.java)

                EditServerScreenCompose(
                    serverId = java.util.UUID.fromString(serverId),
                    startupViewModel = startupViewModel,
                    serverUserRepository = serverUserRepository,
                    onBack = { currentScreen = "auth" },
                    onNavigateToEditUser = { serverIdClicked, userId ->
                        currentServerId = serverIdClicked.toString()
                        currentUserId = userId.toString()
                        currentScreen = "edit_user"
                    }
                )
            }
            "edit_user" -> {
                val serverId = currentServerId
                val userId = currentUserId
                val startupViewModel = KoinJavaComponent.get<org.jellyfin.androidtv.ui.startup.StartupViewModel>(org.jellyfin.androidtv.ui.startup.StartupViewModel::class.java)
                val serverUserRepository = KoinJavaComponent.get<ServerUserRepository>(ServerUserRepository::class.java)
                val authenticationRepository = KoinJavaComponent.get<org.jellyfin.androidtv.auth.repository.AuthenticationRepository>(org.jellyfin.androidtv.auth.repository.AuthenticationRepository::class.java)

                EditUserScreenCompose(
                    serverId = java.util.UUID.fromString(serverId),
                    userId = java.util.UUID.fromString(userId),
                    startupViewModel = startupViewModel,
                    authenticationRepository = authenticationRepository,
                    serverUserRepository = serverUserRepository,
                    onBack = { currentScreen = "edit_server" }
                )
            }
        }
    }
}

@Composable
fun MainPreferencesScreen(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    systemPreferences: SystemPreferences,
    imageLoader: ImageLoader,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.settings_title))
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.pref_login),
				description = context.getString(R.string.pref_login_description),
                icon = R.drawable.ic_login,
                onClick = { onNavigate("auth") },
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.pref_customization),
				description = context.getString(R.string.pref_customization_description),
                icon = R.drawable.ic_adjust,
                onClick = { onNavigate("customization") }
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.pref_playback),
				description = context.getString(R.string.pref_playback_description),
                icon = R.drawable.ic_playback,
                onClick = { onNavigate("playback") }
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.pref_telemetry_category),
				description = context.getString(R.string.pref_telemetry_description),
                icon = R.drawable.ic_crash,
                onClick = { onNavigate("crash_reporting") }
            )
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.pref_developer_link),
                description = context.getString(R.string.pref_developer_link_description),
                icon = R.drawable.ic_flask,
                onClick = { onNavigate("developer") }
            )
        }

        item {
            PreferenceHeader(context.getString(R.string.pref_about_title))
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.app_version),
				description = CURRENT_VERSION,
				icon = R.drawable.dune_logo,
                onClick = { }
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.Check_for_updates),
				description = context.getString(R.string.check_updates_description),
				icon = R.drawable.ic_check_update,
                onClick = {
                    checkForUpdates(context)
                }
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.pref_device_model),
				description = "${Build.MANUFACTURER} ${Build.MODEL}",
				icon = R.drawable.ic_device,
                onClick = { }
            )
        }

        item {
            PreferenceCard(
				title = context.getString(R.string.licenses_link),
				description = context.getString(R.string.open_source_licenses),
				icon = R.drawable.ic_license,
                onClick = {
                    onNavigate("licenses")
                }
            )
        }
    }
}


