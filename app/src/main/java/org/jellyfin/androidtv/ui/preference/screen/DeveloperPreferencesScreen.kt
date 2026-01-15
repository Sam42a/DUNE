package org.jellyfin.androidtv.ui.preference.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import coil3.ImageLoader
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.isTvDevice

@Composable
fun DeveloperPreferencesScreenCompose(
    userPreferences: UserPreferences,
    systemPreferences: SystemPreferences,
    imageLoader: ImageLoader,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var cacheSize by remember {
        mutableStateOf(Formatter.formatFileSize(context, imageLoader.diskCache?.size ?: 0))
    }
    var showRestartDialog by remember { mutableStateOf(false) }
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
            PreferenceHeader(context.getString(R.string.pref_developer_link_description))
        }

        item {
            val (debuggingEnabled, setDebuggingEnabled) = rememberPreferenceState(
                preference = UserPreferences.debuggingEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.lbl_enable_debug),
                checked = debuggingEnabled,
                preference = UserPreferences.debuggingEnabled,
                onCheckedChange = setDebuggingEnabled,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        if (!context.isTvDevice()) {
            item {
                val (disableUiModeWarning, setDisableUiModeWarning) = rememberPreferenceState(
                    preference = SystemPreferences.disableUiModeWarning,
                    preferences = systemPreferences
                )
                SwitchPreference(
                    title = context.getString(R.string.disable_ui_mode_warning),
                    checked = disableUiModeWarning,
                    preference = SystemPreferences.disableUiModeWarning,
                    onCheckedChange = setDisableUiModeWarning
                )
            }
        }


        item {
            val (trickPlayEnabled, setTrickPlayEnabled) = rememberPreferenceState(
                preference = UserPreferences.trickPlayEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.preference_enable_trickplay),
                checked = trickPlayEnabled,
                preference = UserPreferences.trickPlayEnabled,
                onCheckedChange = setTrickPlayEnabled
            )
        }

        item {
            val (preferExoPlayerFfmpeg, setPreferExoPlayerFfmpeg) = rememberPreferenceState(
                preference = UserPreferences.preferExoPlayerFfmpeg,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.prefer_exoplayer_ffmpeg),
                checked = preferExoPlayerFfmpeg,
                preference = UserPreferences.preferExoPlayerFfmpeg,
                onCheckedChange = setPreferExoPlayerFfmpeg
            )
        }

        item {
            val (hardwareAccelerationEnabled, setHardwareAccelerationEnabled) = rememberPreferenceState(
                preference = UserPreferences.hardwareAccelerationEnabled,
                preferences = userPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.hardware_acceleration_enabled),
                checked = hardwareAccelerationEnabled,
                preference = UserPreferences.hardwareAccelerationEnabled,
                onCheckedChange = setHardwareAccelerationEnabled
            )
        }

        item {
            val (diskCacheSize, setDiskCacheSize) = rememberPreferenceState(
                preference = UserPreferences.diskCacheSizeMb,
                preferences = userPreferences
            )
            ListPreference(
                title = context.getString(R.string.pref_disk_cache_size),
                value = diskCacheSize.toString(),
                onValueChange = { newValue ->
                    val newCacheSize = newValue.toInt()
                    if (diskCacheSize != newCacheSize) {
                        setDiskCacheSize(newCacheSize)
                        showRestartDialog = true
                    }
                },
                options = mapOf(
                    "0" to context.getString(R.string.pref_disk_cache_size_disabled),
                    "100" to context.getString(R.string.pref_disk_cache_size_100mb),
                    "250" to context.getString(R.string.pref_disk_cache_size_250mb),
                    "500" to context.getString(R.string.pref_disk_cache_size_500mb),
                    "800" to context.getString(R.string.pref_disk_cache_size_800mb),
                    "1024" to context.getString(R.string.pref_disk_cache_size_1gb),
                    "1536" to context.getString(R.string.pref_disk_cache_size_1_5gb),
                    "2048" to context.getString(R.string.pref_disk_cache_size_2gb)
                ),
                defaultValue = "0"
            )
        }

        item {
            ActionPreference(
                title = context.getString(R.string.clear_image_cache),
                description = cacheSize,
                onClick = {
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                    cacheSize = Formatter.formatFileSize(context, 0)
                }
            )
        }
    }

    // Restart Dialog
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
                        val packageManager = context.packageManager
                        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                        val mainIntent = android.content.Intent.makeRestartActivityTask(intent?.component)
                        context.startActivity(mainIntent)
                        Runtime.getRuntime().exit(0)
                    },
                    modifier = Modifier
                        .focusRequester(confirmFocusRequester)
                        .focusable(interactionSource = confirmInteractionSource)
                        .onKeyEvent { keyEvent ->
                            if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
                                keyEvent.type == KeyEventType.KeyUp) {
                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                val mainIntent = android.content.Intent.makeRestartActivityTask(intent?.component)
                                context.startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
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
