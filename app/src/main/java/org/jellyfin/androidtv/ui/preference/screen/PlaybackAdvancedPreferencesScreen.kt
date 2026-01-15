package org.jellyfin.androidtv.ui.preference.screen

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.getQualityProfiles
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RefreshRateSwitchingBehavior
import org.jellyfin.androidtv.preference.constant.ZoomMode
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.profile.createDeviceProfileReport
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.koin.java.KoinJavaComponent
import timber.log.Timber

@Composable
fun PlaybackAdvancedPreferencesScreenCompose(
    userPreferences: UserPreferences,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val api: ApiClient = KoinJavaComponent.get(ApiClient::class.java)
    val firstItemFocusRequester = remember { FocusRequester() }
    var deviceProfileReported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PreferenceHeader(context.getString(R.string.pref_customization))
            }

            // Resume Preroll
            item {
                val (resumeSubtractDuration, setResumeSubtractDuration) = rememberPreferenceState(
                    preference = UserPreferences.resumeSubtractDuration,
                    preferences = userPreferences
                )

                val prerollOptions = setOf(
                    0, // Disable
                    3, 5, 7, // 10<
                    10, 20, 30, 60, // 100<
                    120, 300
                ).associate {
                    val value = if (it == 0) context.getString(R.string.lbl_none)
                    else TimeUtils.formatSeconds(context, it)

                    it.toString() to value
                }

                ListPreference(
                    title = context.getString(R.string.lbl_resume_preroll),
                    value = resumeSubtractDuration.toString(),
                    onValueChange = { setResumeSubtractDuration(it) },
                    options = prerollOptions,
                    defaultValue = resumeSubtractDuration.toString(),
                    description = prerollOptions[resumeSubtractDuration.toString()],
                    modifier = Modifier.focusRequester(firstItemFocusRequester)
                )
            }

            // TV Queuing
            item {
                val (mediaQueuingEnabled, setMediaQueuingEnabled) = rememberPreferenceState(
                    preference = UserPreferences.mediaQueuingEnabled,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.lbl_tv_queuing),
                    checked = mediaQueuingEnabled,
                    preference = UserPreferences.mediaQueuingEnabled,
                    onCheckedChange = setMediaQueuingEnabled
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.pref_video))
            }

            // Max Bitrate
            item {
                val (maxBitrate, setMaxBitrate) = rememberPreferenceState(
                    preference = UserPreferences.maxBitrate,
                    preferences = userPreferences
                )

                ListPreference(
                    title = context.getString(R.string.pref_max_bitrate_title),
                    value = maxBitrate.toString(),
                    onValueChange = { setMaxBitrate(it) },
                    options = getQualityProfiles(context),
                    defaultValue = "0",
                    description = getQualityProfiles(context)[maxBitrate.toString()] ?: "Auto"
                )
            }

            // Refresh Rate Switching
            item {
                val (refreshRateSwitchingBehavior, setRefreshRateSwitchingBehavior) = rememberEnumPreferenceState(
                    preference = UserPreferences.refreshRateSwitchingBehavior,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.lbl_refresh_switching),
                    value = refreshRateSwitchingBehavior,
                    onValueChange = setRefreshRateSwitchingBehavior,
                    options = RefreshRateSwitchingBehavior.entries.toList(),
                    optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                    description = context.getString((refreshRateSwitchingBehavior as org.jellyfin.preference.PreferenceEnum).nameRes)
                )
            }

            // Video Start Delay
            item {
                val (videoStartDelay, setVideoStartDelay) = rememberPreferenceState(
                    preference = UserPreferences.videoStartDelay,
                    preferences = userPreferences
                )

                SeekBarPreference(
                    title = context.getString(R.string.video_start_delay),
                    value = videoStartDelay.toInt(),
                    range = 0..5_000,
                    step = 250,
                    description = "${videoStartDelay.toDouble() / 1000}s",
                    valueFormatter = { "${it.toDouble() / 1000}s" },
                    onValueChange = { setVideoStartDelay(it.toLong()) }
                )
            }

            // Default Video Zoom
            item {
                val (playerZoomMode, setPlayerZoomMode) = rememberEnumPreferenceState(
                    preference = UserPreferences.playerZoomMode,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.default_video_zoom),
                    value = playerZoomMode,
                    onValueChange = setPlayerZoomMode,
                    options = ZoomMode.entries.toList(),
                    optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                    description = context.getString((playerZoomMode as org.jellyfin.preference.PreferenceEnum).nameRes)
                )
            }

            // Use External Player
            item {
                val (useExternalPlayer, setUseExternalPlayer) = rememberPreferenceState(
                    preference = UserPreferences.useExternalPlayer,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.pref_external_player),
                    checked = useExternalPlayer,
                    preference = UserPreferences.useExternalPlayer,
                    onCheckedChange = setUseExternalPlayer
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.home_section_livetv),)
            }

            // Direct Stream Live TV
            item {
                val (liveTvDirectPlayEnabled, setLiveTvDirectPlayEnabled) = rememberPreferenceState(
                    preference = UserPreferences.liveTvDirectPlayEnabled,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.lbl_direct_stream_live),
                    checked = liveTvDirectPlayEnabled,
                    preference = UserPreferences.liveTvDirectPlayEnabled,
                    onCheckedChange = setLiveTvDirectPlayEnabled
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.pref_audio),)
            }

            // Audio Night Mode
            item {
                val (audioNightMode, setAudioNightMode) = rememberPreferenceState(
                    preference = UserPreferences.audioNightMode,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.pref_audio_night_mode),
                    description = context.getString(R.string.desc_audio_night_mode),
                    checked = audioNightMode,
                    preference = UserPreferences.audioNightMode,
                    onCheckedChange = setAudioNightMode,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                )
            }

            // Bitstream AC3
            item {
                val (ac3Enabled, setAc3Enabled) = rememberPreferenceState(
                    preference = UserPreferences.ac3Enabled,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title =  context.getString(R.string.lbl_bitstream_ac3),
                    description = context.getString(R.string.desc_bitstream_ac3),
                    checked = ac3Enabled,
                    preference = UserPreferences.ac3Enabled,
                    onCheckedChange = setAc3Enabled
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.lbl_Subtitles),)
            }

            // Enable PGS
            item {
                val (pgsDirectPlay, setPgsDirectPlay) = rememberPreferenceState(
                    preference = UserPreferences.pgsDirectPlay,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.preference_enable_pgs),
                    checked = pgsDirectPlay,
                    preference = UserPreferences.pgsDirectPlay,
                    onCheckedChange = setPgsDirectPlay
                )
            }
			// Enable ASS
			item {
				val (assDirectPlay, setAssDirectPlay) = rememberPreferenceState(
					preference = UserPreferences.assDirectPlay,
					preferences = userPreferences
				)

				SwitchPreference(
					title = context.getString(R.string.preference_enable_assDirectPlay),
					checked = assDirectPlay,
					preference = UserPreferences.assDirectPlay,
					onCheckedChange = setAssDirectPlay
				)
			}

            item {
                PreferenceHeader(context.getString(R.string.pref_troubleshooting),)
            }

            // Report Device Profile
            item {
                ActionPreference(
                    title = context.getString(R.string.pref_report_device_profile_title),
                    description = context.getString(R.string.pref_report_device_profile_summary),
                    icon = R.drawable.ic_more,
                    onClick = {
                        if (!deviceProfileReported) {
                            deviceProfileReported = true

                            CoroutineScope(Dispatchers.Main).launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        api.clientLogApi.logFile(createDeviceProfileReport(context, userPreferences)).content
                                    }
                                }.fold(
                                    onSuccess = { result ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.pref_report_device_profile_success, result.fileName),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onFailure = { error ->
                                        Timber.e(error, "Failed to upload device profile")
                                        Toast.makeText(context, R.string.pref_report_device_profile_failure, Toast.LENGTH_LONG).show()
                                        deviceProfileReported = false
                                    }
                                )
                            }
                        }
                    },
                    enabled = !deviceProfileReported
                )
            }
        }
    }
}
