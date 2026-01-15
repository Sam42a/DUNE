package org.jellyfin.androidtv.ui.preference.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.AppLanguage
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.preference.constant.CarouselSortBy
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.preference.constant.ScreensaverSortBy
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior
import org.jellyfin.androidtv.util.getQuantityString
import kotlin.system.exitProcess

@Composable
fun CustomizationPreferencesScreenCompose(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    onBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToLibraries: () -> Unit = {},
    onNavigateToBackdropSettings: () -> Unit = {},
    onNavigateToThemeSongs: () -> Unit = {},
    onNavigateToGenres: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRestartDialog by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

	val (imageQuality, setImageQuality) = rememberPreferenceState(
		preference = UserPreferences.imageQuality,
		preferences = userPreferences
	)

    // Function to handle app restart
    fun handleAppRestart() {
        try {
            // Get the current activity
            val activity = context as? Activity

            activity?.let {
                // Create an intent to restart the app
                val packageManager = it.packageManager
                val intent = packageManager.getLaunchIntentForPackage(it.packageName)
                val componentName = intent?.component

                // Create a fresh task with the launcher activity
                val mainIntent = Intent.makeRestartActivityTask(componentName)

                // Add flags to clear the back stack and create a new task
                mainIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )

                // Add a small delay to ensure the activity is properly finished
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Start the new activity
                        it.startActivity(mainIntent)

                        // Kill the current process to ensure a clean restart
                        Process.killProcess(Process.myPid())
						exitProcess(0)
                    } catch (e: Exception) {
                        // Only log errors
                    }
                }, 200)
            }
        } catch (e: Exception) {
            // Only log errors
        }
    }

    // Get theme songs enabled state for use in multiple items
    val (themeSongsEnabled, setThemeSongsEnabled) = rememberPreferenceState(
        preference = userSettingPreferences.themeSongsEnabled,
        preferences = userSettingPreferences
    )

    // Get screensaver enabled state for use in multiple items
    val (screensaverInAppEnabled, setScreensaverInAppEnabled) = rememberPreferenceState(
        preference = UserPreferences.screensaverInAppEnabled,
        preferences = userPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.pref_customization))
        }

        item {
            val (appLanguage, setAppLanguage) = rememberEnumPreferenceState(
                preference = UserPreferences.appLanguage,
                preferences = userPreferences
            )
            EnumPreference(
				title = context.getString(R.string.pref_language),
				description = context.getString(R.string.pref_language_summary),
				value = appLanguage,
				onValueChange = { newLanguage ->
					// Save to SharedPreferences directly to ensure it's persisted
					val prefs = context.getSharedPreferences("org.jellyfin.androidtv.preferences", Context.MODE_PRIVATE)
					prefs.edit().putString("app_language", newLanguage.code).apply()

					// Also save through userPreferences for consistency
					userPreferences[UserPreferences.appLanguage] = newLanguage

					// Show restart dialog for language changes
					showRestartDialog = true
				},
				options = AppLanguage.entries,
				optionLabel = { it.displayName },
				modifier = Modifier.focusRequester(firstItemFocusRequester)
            )

        }

		item {
			val (appTheme, setAppTheme) = rememberEnumPreferenceState(
				preference = UserPreferences.appTheme,
				preferences = userPreferences
			)
			EnumPreference(
				title = context.getString(R.string.pref_app_theme),
				value = appTheme,
				onValueChange = setAppTheme,
				options = AppTheme.entries,
				optionLabel = { context.getString(it.nameRes) }
			)
		}

		item {
			val (useClassicHomeScreen, setUseClassicHomeScreen) = rememberPreferenceState(
				preference = userSettingPreferences.useClassicHomeScreen,
				preferences = userSettingPreferences
			)
			SwitchPreference(
				title = context.getString(R.string.use_classic_home_screen),
				description = context.getString(R.string.use_classic_home_screen_summary),
				checked = useClassicHomeScreen,
				preference = userSettingPreferences.useClassicHomeScreen,
				onCheckedChange = setUseClassicHomeScreen
			)
		}

        item {
            PreferenceHeader(context.getString(R.string.pref_browsing))
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.lbl_home),
                description = context.getString(R.string.home_sections),
                icon = R.drawable.ic_sections,
                onClick = { onNavigateToHome() }
            )
        }

		item {
			PreferenceCard(
				title = context.getString(R.string.home_custom_sections),
				description = context.getString(R.string.genre_row_prefs),
				icon = R.drawable.ic_masks,
				onClick = { onNavigateToGenres() }
			)
		}

        item {
            PreferenceCard(
                title = context.getString(R.string.pref_libraries),
                description = context.getString(R.string.pref_libraries_description),
                icon = R.drawable.ic_libraries,
                onClick = {
                    // Navigate to LibrariesPreferencesScreen
                    onNavigateToLibraries()
                }
            )
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.backdrop_settings),
                description = context.getString(R.string.backdrop_settings_description),
                icon = R.drawable.ic_backdrop,
                onClick = { onNavigateToBackdropSettings() }
            )
        }



        item {
            val (watchedIndicator, setWatchedIndicator) = rememberEnumPreferenceState(
                preference = UserPreferences.watchedIndicatorBehavior,
                preferences = userPreferences
            )
            EnumPreference(
				title = context.getString(R.string.pref_watched_indicator),
				description = context.getString(R.string.pref_watched_indicator_description),
				value = watchedIndicator,
				onValueChange = { newValue ->
					setWatchedIndicator(newValue)
				},
				options = WatchedIndicatorBehavior.entries,
				optionLabel = { context.getString(it.nameRes) }
			)
        }

        item {
            val (showResolutionBadge, setShowResolutionBadge) = rememberPreferenceState(
                preference = UserPreferences.showResolutionBadge,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_show_resolution_badge),
                checked = showResolutionBadge,
                preference = UserPreferences.showResolutionBadge,
                onCheckedChange = setShowResolutionBadge
            )
        }

        item {
            val (defaultRating, setDefaultRating) = rememberEnumPreferenceState(
                preference = UserPreferences.defaultRatingType,
                preferences = userPreferences
            )
            EnumPreference(
				title = context.getString(R.string.lbl_rating),
				description = context.getString(R.string.pref_default_rating),
				value = defaultRating,
				onValueChange = { newValue ->
					setDefaultRating(newValue)
				},
				options = RatingType.entries,
				optionLabel = { context.getString(it.nameRes) }
			)
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_theme_songs_enable),
                checked = themeSongsEnabled,
                preference = userSettingPreferences.themeSongsEnabled,
                onCheckedChange = setThemeSongsEnabled
            )
        }

        if (themeSongsEnabled) {
            item {
                val (themeSongVolume, setThemeSongVolume) = rememberPreferenceState(
                    preference = userSettingPreferences.themesongvolume,
                    preferences = userSettingPreferences
                )
                ListPreference(
                    title = context.getString(R.string.pref_theme_songs_volume),
                    value = themeSongVolume.toString(),
                    onValueChange = { newValue ->
                        setThemeSongVolume(newValue.toInt())
                    },
                    options = mapOf(
                        "5" to context.getString(R.string.pref_theme_song_volume_very_low),
                        "15" to context.getString(R.string.pref_theme_song_volume_low),
                        "30" to context.getString(R.string.pref_theme_song_volume_normal),
                        "60" to context.getString(R.string.pref_theme_song_volume_high),
                        "100" to context.getString(R.string.pref_theme_song_volume_very_high)
                    ),
                    defaultValue = "30"
                )
            }
        }

        if (themeSongsEnabled) {
            item {
                PreferenceCard(
                    title = context.getString(R.string.pref_theme_song_media_types),
                    description = context.getString(R.string.pref_theme_song_media_types_summary),
                    icon = R.drawable.ic_select_audio,
                    onClick = { onNavigateToThemeSongs() }
                )
            }
        }

        item {
            val (premieresEnabled, setPremieresEnabled) = rememberPreferenceState(
                preference = UserPreferences.premieresEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_show_premieres),
                checked = premieresEnabled,
                preference = UserPreferences.premieresEnabled,
                onCheckedChange = setPremieresEnabled
            )
        }

        item {
            val (mediaManagementEnabled, setMediaManagementEnabled) = rememberPreferenceState(
                preference = UserPreferences.mediaManagementEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_enable_media_management),
                checked = mediaManagementEnabled,
                preference = UserPreferences.mediaManagementEnabled,
                onCheckedChange = setMediaManagementEnabled
            )
        }

		item {
			ListPreference(
				title = context.getString(R.string.image_quality),
				value = imageQuality,
				onValueChange = setImageQuality,
				options = mapOf(
					"low" to context.getString(R.string.image_quality_low),
					"normal" to context.getString(R.string.image_quality_normal),
					"high" to context.getString(R.string.image_quality_high)
				),
				defaultValue = UserPreferences.imageQuality.defaultValue
			)
		}

        // Enhanced Tweaks - Browsing Preferences
        item {
            PreferenceHeader(context.getString(R.string.enhanced_tweaks))
        }

        item {
            val (showLiveTvButton, setShowLiveTvButton) = rememberPreferenceState(
                preference = userSettingPreferences.showLiveTvButton,
                preferences = userSettingPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.show_live_tv_button),
                description = context.getString(R.string.show_live_tv_button_summary),
                checked = showLiveTvButton,
                preference = userSettingPreferences.showLiveTvButton,
                onCheckedChange = setShowLiveTvButton
            )
        }

        item {
            val (showRandomButton, setShowRandomButton) = rememberPreferenceState(
                preference = userSettingPreferences.showRandomButton,
                preferences = userSettingPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.show_random_button),
                description = context.getString(R.string.show_random_button_summary),
                checked = showRandomButton,
                preference = userSettingPreferences.showRandomButton,
                onCheckedChange = setShowRandomButton
            )
        }

        item {
            val (carouselSortBy, setCarouselSortBy) = rememberEnumPreferenceState(
                preference = UserPreferences.carouselSortBy,
                preferences = userPreferences
            )
            EnumPreference(
                title = context.getString(R.string.pref_carousel_sort_by),
                value = carouselSortBy,
                onValueChange = setCarouselSortBy,
                options = CarouselSortBy.entries,
                optionLabel = { context.getString(it.nameRes) }
            )
        }

        item {
            val (snowfallEnabled, setSnowfallEnabled) = rememberPreferenceState(
                preference = UserPreferences.snowfallEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.pref_snowfall_enabled),
                description = context.getString(R.string.pref_snowfall_enabled_description),
                checked = snowfallEnabled,
                preference = UserPreferences.snowfallEnabled,
                onCheckedChange = setSnowfallEnabled
            )
        }

        item {
            val (carouselIncludeSeries, setCarouselIncludeSeries) = rememberPreferenceState(
                preference = UserPreferences.carouselIncludeSeries,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.pref_carousel_include_series),
                description = context.getString(R.string.pref_carousel_include_series_description),
                checked = carouselIncludeSeries,
                preference = UserPreferences.carouselIncludeSeries,
                onCheckedChange = setCarouselIncludeSeries
            )
        }

        item {
            val (seriesThumbnailsEnabled, setSeriesThumbnailsEnabled) = rememberPreferenceState(
                preference = UserPreferences.seriesThumbnailsEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_use_series_thumbnails),
                description = context.getString(R.string.lbl_use_series_thumbnails_description),
                checked = seriesThumbnailsEnabled,
                preference = UserPreferences.seriesThumbnailsEnabled,
                onCheckedChange = setSeriesThumbnailsEnabled
            )
        }

        // Android TV Channels
        item {
            PreferenceHeader(context.getString(R.string.android_channels))
        }

        item {
            val (launcherThumbnailsEnabled, setLauncherThumbnailsEnabled) = rememberPreferenceState(
                preference = UserPreferences.launcherThumbnailsEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_use_launcher_thumbnails),
                description = context.getString(R.string.lbl_use_launcher_thumbnails_description),
                checked = launcherThumbnailsEnabled,
                preference = UserPreferences.launcherThumbnailsEnabled,
                onCheckedChange = setLauncherThumbnailsEnabled
            )
        }

        item {
            val (launcherChannelsEnabled, setLauncherChannelsEnabled) = rememberPreferenceState(
                preference = UserPreferences.launcherChannelsEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_enable_launcher_channels),
                description = context.getString(R.string.lbl_enable_launcher_channels_description),
                checked = launcherChannelsEnabled,
                preference = UserPreferences.launcherChannelsEnabled,
                onCheckedChange = setLauncherChannelsEnabled
            )
        }

        item {
            PreferenceHeader(context.getString(R.string.pref_screensaver))
        }

        item {
            SwitchPreference(
                title = context.getString(R.string.pref_screensaver_inapp_enabled),
                checked = screensaverInAppEnabled,
                preference = UserPreferences.screensaverInAppEnabled,
                onCheckedChange = setScreensaverInAppEnabled
            )
        }

        if (screensaverInAppEnabled) {
            item {
                val (screensaverTimeout, setScreensaverTimeout) = rememberPreferenceState(
                    preference = UserPreferences.screensaverInAppTimeout,
                    preferences = userPreferences
                )
                ListPreference(
                    title = context.getString(R.string.pref_screensaver_inapp_timeout),
                    value = screensaverTimeout.toString(),
                    onValueChange = { newValue ->
                        setScreensaverTimeout(newValue.toLong())
                    },
                    options = mapOf(
                        "30000" to context.getQuantityString(R.plurals.seconds, 30),
                        "60000" to context.getQuantityString(R.plurals.minutes, 1),
                        "150000" to context.getQuantityString(R.plurals.minutes, 2.5),
                        "300000" to context.getQuantityString(R.plurals.minutes, 5),
                        "600000" to context.getQuantityString(R.plurals.minutes, 10),
                        "900000" to context.getQuantityString(R.plurals.minutes, 15),
                        "1800000" to context.getQuantityString(R.plurals.minutes, 30)
                    ),
                    defaultValue = UserPreferences.screensaverInAppTimeout.defaultValue.toString()
                )
            }
        }

        if (screensaverInAppEnabled) {
            item {
                val (screensaverSortBy, setScreensaverSortBy) = rememberEnumPreferenceState(
                    preference = UserPreferences.screensaverSortBy,
                    preferences = userPreferences
                )
                EnumPreference(
					title = context.getString(R.string.pref_screensaver_sort_by),
					value = screensaverSortBy,
					onValueChange = { newValue ->
						setScreensaverSortBy(newValue)
					},
					options = ScreensaverSortBy.entries,
					optionLabel = { context.getString(it.nameRes) }
				)
            }
        }

        item {
            val (screensaverAgeRatingRequired, setScreensaverAgeRatingRequired) = rememberPreferenceState(
                preference = UserPreferences.screensaverAgeRatingRequired,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_age_rating_required),
                checked = screensaverAgeRatingRequired,
                preference = UserPreferences.screensaverAgeRatingRequired,
                onCheckedChange = setScreensaverAgeRatingRequired
            )
        }

        item {
            val (screensaverClockEnabled, setScreensaverClockEnabled) = rememberPreferenceState(
                preference = UserPreferences.screensaverClockEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.pref_screensaver_clock_enabled),
                checked = screensaverClockEnabled,
                preference = UserPreferences.screensaverClockEnabled,
                onCheckedChange = setScreensaverClockEnabled
            )
        }

        item {
            val (screensaverLogoEnabled, setScreensaverLogoEnabled) = rememberPreferenceState(
                preference = UserPreferences.screensaverLogoEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.pref_screensaver_logo_enabled),
                checked = screensaverLogoEnabled,
                preference = UserPreferences.screensaverLogoEnabled,
                onCheckedChange = setScreensaverLogoEnabled
            )
        }

        item {
            PreferenceHeader(context.getString(R.string.pref_button_remapping_title))
        }

        item {
            val (audioTrackShortcut, setAudioTrackShortcut) = rememberPreferenceState(
                preference = UserPreferences.shortcutAudioTrack,
                preferences = userPreferences
            )
            ButtonRemapPreference(
                title = context.getString(R.string.pref_audio_track_button),
                value = audioTrackShortcut,
                defaultValue = KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                onValueChange = { newValue ->
                    setAudioTrackShortcut(newValue)
                }
            )
        }

        item {
            val (subtitleTrackShortcut, setSubtitleTrackShortcut) = rememberPreferenceState(
                preference = UserPreferences.shortcutSubtitleTrack,
                preferences = userPreferences
            )
            ButtonRemapPreference(
                title = context.getString(R.string.pref_subtitle_track_button),
                value = subtitleTrackShortcut,
                defaultValue = KeyEvent.KEYCODE_CAPTIONS,
                onValueChange = { newValue ->
                    setSubtitleTrackShortcut(newValue)
                }
            )
        }
    }

    // Restart Dialog for Language Changes
    if (showRestartDialog) {
        val confirmInteractionSource = remember { MutableInteractionSource() }
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val isConfirmFocused by confirmInteractionSource.collectIsFocusedAsState()
        val isDismissFocused by dismissInteractionSource.collectIsFocusedAsState()
        val confirmFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            confirmFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    text = context.getString(R.string.restart_required),
                    color = Color.White
                )
            },
            containerColor = Color.Black,
            textContentColor = Color.White,
            text = {
                Text(
                    text = context.getString(R.string.restart_required_message),
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        handleAppRestart()
                    },
                    modifier = Modifier
                        .focusRequester(confirmFocusRequester)
                        .focusable(interactionSource = confirmInteractionSource)
                        .onKeyEvent { keyEvent ->
                            if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
                                keyEvent.type == KeyEventType.KeyUp) {
                                showRestartDialog = false
                                handleAppRestart()
                                true
                            } else {
                                false
                            }
                        }
                        .background(
                            if (isConfirmFocused) Color(0x40FFFFFF) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = context.getString(R.string.restart_now),
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false },
                    modifier = Modifier
                        .focusable(interactionSource = dismissInteractionSource)
                        .onKeyEvent { keyEvent ->
                            if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
                                keyEvent.type == KeyEventType.KeyUp) {
                                showRestartDialog = false
                                true
                            } else {
                                false
                            }
                        }
                        .background(
                            if (isDismissFocused) Color(0x40FFFFFF) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = context.getString(R.string.restart_later),
                        color = Color.White
                    )
                }
            }
        )
    }
}
