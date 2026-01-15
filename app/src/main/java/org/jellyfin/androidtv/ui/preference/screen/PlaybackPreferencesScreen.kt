package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.AudioBehavior
import org.jellyfin.androidtv.preference.constant.AudioLanguage
import org.jellyfin.androidtv.preference.constant.NEXTUP_TIMER_DISABLED
import org.jellyfin.androidtv.preference.constant.NextUpBehavior
import org.jellyfin.androidtv.preference.constant.SkipDuration
import org.jellyfin.androidtv.preference.constant.SubtitleLanguage
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentAction
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlin.math.roundToInt

@Composable
fun PlaybackPreferencesScreenCompose(
    userPreferences: UserPreferences,
    userSettingPreferences: UserSettingPreferences,
    mediaSegmentRepository: MediaSegmentRepository,
    onBack: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    // Declare subtitle preference states in main composable scope
    val (subtitlesTextColor, setSubtitlesTextColor) = rememberPreferenceState(
        preference = UserPreferences.subtitlesTextColor,
        preferences = userPreferences
    )
    val (subtitlesBackgroundColor, setSubtitlesBackgroundColor) = rememberPreferenceState(
        preference = UserPreferences.subtitlesBackgroundColor,
        preferences = userPreferences
    )
    val (subtitlesTextSize, setSubtitlesTextSize) = rememberPreferenceState(
        preference = UserPreferences.subtitlesTextSize,
        preferences = userPreferences
    )
    val (subtitlesTextWeightValue, setSubtitlesTextWeightValue) = rememberPreferenceState(
        preference = UserPreferences.subtitlesTextWeightValue,
        preferences = userPreferences
    )

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but bruh just continue
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

            // Next Up Behavior
            item {
                val (nextUpBehavior, setNextUpBehavior) = rememberEnumPreferenceState(
                    preference = UserPreferences.nextUpBehavior,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.pref_next_up_behavior_title),
                    value = nextUpBehavior,
                    onValueChange = setNextUpBehavior,
                    options = NextUpBehavior.entries.toList(),
                    optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                    description = context.getString((nextUpBehavior as org.jellyfin.preference.PreferenceEnum).nameRes),
                    modifier = Modifier.focusRequester(firstItemFocusRequester)
                )
            }

            // Next Up Timeout (only if media queuing is enabled and next up is not disabled)
            item {
                val (mediaQueuingEnabled, setMediaQueuingEnabled) = rememberPreferenceState(
                    preference = UserPreferences.mediaQueuingEnabled,
                    preferences = userPreferences
                )
                val (nextUpBehavior, _) = rememberEnumPreferenceState(
                    preference = UserPreferences.nextUpBehavior,
                    preferences = userPreferences
                )
                val (nextUpTimeout, setNextUpTimeout) = rememberPreferenceState(
                    preference = UserPreferences.nextUpTimeout,
                    preferences = userPreferences
                )

                SeekBarPreference(
                    title = context.getString(R.string.pref_next_up_timeout_title),
                    value = nextUpTimeout,
                    range = 0..30_000,
                    step = 1_000,
                    description = when (nextUpTimeout) {
                        NEXTUP_TIMER_DISABLED -> context.getString(R.string.pref_next_up_timeout_disabled)
                        else -> "${nextUpTimeout / 1000}s"
                    },
                    valueFormatter = { value -> when (value) {
                        NEXTUP_TIMER_DISABLED -> context.getString(R.string.pref_next_up_timeout_disabled)
                        else -> "${value / 1000}s"
                    }},
                    onValueChange = setNextUpTimeout
                )
            }

            // Cinema Mode
            item {
                val (cinemaModeEnabled, setCinemaModeEnabled) = rememberPreferenceState(
                    preference = UserPreferences.cinemaModeEnabled,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.lbl_enable_cinema_mode),
                    description = context.getString(R.string.sum_enable_cinema_mode),
                    checked = cinemaModeEnabled,
                    preference = UserPreferences.cinemaModeEnabled,
                    onCheckedChange = setCinemaModeEnabled
                )
            }

            // Skip Forward Length
            item {
                val (skipForwardLength, setSkipForwardLength) = rememberPreferenceState(
                    preference = userSettingPreferences.skipForwardLength,
                    preferences = userSettingPreferences
                )

                SeekBarPreference(
                    title = context.getString(R.string.skip_forward_length),
                    value = skipForwardLength,
                    range = 5_000..30_000,
                    step = 5_000,
                    description = "${skipForwardLength / 1000}s",
                    valueFormatter = { "${it / 1000}s" },
                    onValueChange = setSkipForwardLength
                )
            }

            // Player Controls Hide Duration
            item {
                val (playerControlsHideDuration, setPlayerControlsHideDuration) = rememberPreferenceState(
                    preference = UserPreferences.playerControlsHideDuration,
                    preferences = userPreferences
                )

                SeekBarPreference(
                    title = context.getString(R.string.player_controls_hide_duration),
                    value = playerControlsHideDuration,
                    range = 1_000..30_000,
                    step = 1_000,
                    description = "${playerControlsHideDuration / 1000}s",
                    valueFormatter = { "${it / 1000}s" },
                    onValueChange = setPlayerControlsHideDuration
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.pref_audio))
            }

            // Audio Output
            item {
                val (audioBehavior, setAudioBehavior) = rememberEnumPreferenceState(
                    preference = UserPreferences.audioBehaviour,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.lbl_audio_output),
                    value = audioBehavior,
                    onValueChange = setAudioBehavior,
                    options = AudioBehavior.entries.toList(),
                    optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                    description = context.getString((audioBehavior as org.jellyfin.preference.PreferenceEnum).nameRes)
                )
            }

            // Default Audio Language
            item {
                val (defaultAudioLanguage, setDefaultAudioLanguage) = rememberEnumPreferenceState(
                    preference = UserPreferences.defaultAudioLanguage,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.pref_audio_default_language),
                    value = defaultAudioLanguage,
                    onValueChange = setDefaultAudioLanguage,
                    options = AudioLanguage.entries.toList(),
                    description = defaultAudioLanguage.displayName
                )
            }

            // Skip Commentary Tracks
            item {
                val (skipCommentaryTracks, setSkipCommentaryTracks) = rememberPreferenceState(
                    preference = UserPreferences.skipCommentaryTracks,
                    preferences = userPreferences
                )

                SwitchPreference(
                    title = context.getString(R.string.pref_skip_commentary_tracks),
                    description = context.getString(R.string.pref_skip_commentary_tracks_description),
                    checked = skipCommentaryTracks,
                    preference = UserPreferences.skipCommentaryTracks,
                    onCheckedChange = setSkipCommentaryTracks
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.pref_subtitles))
            }

            // Subtitle Preview
            item {
                SubtitlePreviewPreference(
                    textColor = Color(subtitlesTextColor),
                    backgroundColor = Color(subtitlesBackgroundColor),
                    textSize = subtitlesTextSize * 24f,
                    isBold = subtitlesTextWeightValue >= 600
                )
            }

            // Subtitle Text Color
            item {
                ColorPreference(
                    title = context.getString(R.string.lbl_subtitle_text_color),
                    value = Color(subtitlesTextColor),
                    onValueChange = { setSubtitlesTextColor(it.toArgb().toLong()) },
                    options = mapOf(
						Color(0xFFFFFFFFL) to context.getString(R.string.color_white),
						Color(0XFF000000L) to context.getString(R.string.color_black),
						Color(0xFF7F7F7FL) to context.getString(R.string.color_darkgrey),
						Color(0xFFC80000L) to context.getString(R.string.color_red),
						Color(0xFF00C800L) to context.getString(R.string.color_green),
						Color(0xFF0000C8L) to context.getString(R.string.color_blue),
						Color(0xFFEEDC00L) to context.getString(R.string.color_yellow),
						Color(0xFFD60080L) to context.getString(R.string.color_pink),
						Color(0xFF009FDAL) to context.getString(R.string.color_cyan),
                    )
                )
            }

            // Subtitle Background Color and Opacity
            item {
                Column {
                    // Background Color Preference - using exact logic from original DSL
                    ColorPreference(
                        title = context.getString(R.string.lbl_subtitle_background_color),
                        value = Color(subtitlesBackgroundColor)
                            .let { it.copy(alpha = if (it.alpha == 0f) 0f else 1f) },
                        onValueChange = { newColor ->
                            // Exact logic from original DSL bind/set
                            val currentAlpha = Color(subtitlesBackgroundColor).alpha
                                .takeIf { alpha -> alpha != 0f }
                                ?: 1f
                            val finalColor = newColor.copy(alpha = if (newColor.alpha == 0f) 0f else currentAlpha)
                            setSubtitlesBackgroundColor(finalColor.toArgb().toLong())
                        },
                        options = mapOf(
							Color(0x00FFFFFFL) to context.getString(R.string.lbl_none),
							Color(0xFFFFFFFFL) to context.getString(R.string.color_white),
							Color(0XFF000000L) to context.getString(R.string.color_black),
							Color(0xFF7F7F7FL) to context.getString(R.string.color_darkgrey),
							Color(0xFFC80000L) to context.getString(R.string.color_red),
							Color(0xFF00C800L) to context.getString(R.string.color_green),
							Color(0xFF0000C8L) to context.getString(R.string.color_blue),
							Color(0xFFEEDC00L) to context.getString(R.string.color_yellow),
							Color(0xFFD60080L) to context.getString(R.string.color_pink),
							Color(0xFF009FDAL) to context.getString(R.string.color_cyan),
                        )
                    )

                    // Background Opacity Preference - using exact logic from original DSL
                    if (Color(subtitlesBackgroundColor).alpha > 0f) {
                        val currentAlpha = (Color(subtitlesBackgroundColor).alpha * 100f).roundToInt()

                        SeekBarPreference(
                            title = context.getString(R.string.pref_subtitles_background_opacity),
                            value = currentAlpha,
                            range = 20..100,
                            step = 10,
                            description = "$currentAlpha%",
                            valueFormatter = { "$it%" },
                            onValueChange = { value ->
                                // Exact logic from original DSL bind/set
                                setSubtitlesBackgroundColor(
                                    Color(subtitlesBackgroundColor)
                                        .copy(alpha = value / 100f)
                                        .toArgb()
                                        .toLong()
                                )
                            }
                        )
                    }
                }
            }

            // Subtitles Bold
            item {
                val boldWeight = 700
                val normalWeight = 400

                val isBold = subtitlesTextWeightValue == boldWeight
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                // Custom switch preference implementation matching SwitchPreference styling exactly
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .focusable(interactionSource = interactionSource)
                        .onKeyEvent { keyEvent ->
                            if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
                                keyEvent.type == KeyEventType.KeyUp) {
                                val newWeight = if (isBold) normalWeight else boldWeight
                                setSubtitlesTextWeightValue(newWeight)
                                true
                            } else {
                                false
                            }
                        }
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) Colors.FocusedBorder else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            when {
                                isFocused -> Colors.FocusedOverlay
                                else -> Colors.Surface
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.pref_subtitles_bold),
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = Colors.OnSurface
                        )
                        Text(
                            text = context.getString(R.string.pref_subtitles_bold_description),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = Colors.OnSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    androidx.compose.material3.Switch(
                        checked = isBold,
                        onCheckedChange = { checked ->
                            val newWeight = if (checked) boldWeight else normalWeight
                            setSubtitlesTextWeightValue(newWeight)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Colors.Primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Colors.Divider
                        )
                    )
                }
            }

            // Default Subtitle Language
            item {
                val (defaultSubtitleLanguage, setDefaultSubtitleLanguage) = rememberEnumPreferenceState(
                    preference = UserPreferences.defaultSubtitleLanguage,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.pref_subtitle_default_language),
                    value = defaultSubtitleLanguage,
                    onValueChange = setDefaultSubtitleLanguage,
                    options = SubtitleLanguage.entries.toList(),
                    description = defaultSubtitleLanguage.displayName
                )
            }

            // Subtitle Text Stroke Color
            item {
                val (subtitleTextStrokeColor, setSubtitleTextStrokeColor) = rememberPreferenceState(
                    preference = UserPreferences.subtitleTextStrokeColor,
                    preferences = userPreferences
                )

                ColorPreference(
                    title = context.getString(R.string.lbl_subtitle_text_stroke_color),
                    value = Color(subtitleTextStrokeColor),
                    onValueChange = { setSubtitleTextStrokeColor(it.toArgb().toLong()) },
                    options = mapOf(
						Color(0x00FFFFFFL) to context.getString(R.string.lbl_none),
						Color(0xFFFFFFFFL) to context.getString(R.string.color_white),
						Color(0XFF000000L) to context.getString(R.string.color_black),
						Color(0xFF7F7F7FL) to context.getString(R.string.color_darkgrey),
						Color(0xFFC80000L) to context.getString(R.string.color_red),
						Color(0xFF00C800L) to context.getString(R.string.color_green),
						Color(0xFF0000C8L) to context.getString(R.string.color_blue),
						Color(0xFFEEDC00L) to context.getString(R.string.color_yellow),
						Color(0xFFD60080L) to context.getString(R.string.color_pink),
						Color(0xFF009FDAL) to context.getString(R.string.color_cyan),
                    )
                )
            }

            // Subtitle Size
            item {
                val currentSize = (subtitlesTextSize * 100f).roundToInt()

                SeekBarPreference(
                    title = context.getString(R.string.pref_subtitles_size),
                    value = currentSize,
                    range = 25..250,
                    step = 25,
                    description = "$currentSize%",
                    valueFormatter = { "$it%" },
                    onValueChange = { value ->
                        setSubtitlesTextSize(value / 100f)
                    }
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.pref_mediasegment_actions),)
            }

            // Media Segment Actions for each type
            MediaSegmentType.entries.forEach { segmentType ->
                item {
                    val (defaultAction, setDefaultAction) = remember {
                        mutableStateOf(mediaSegmentRepository.getDefaultSegmentTypeAction(segmentType))
                    }

                    EnumPreference(
                        title = when (segmentType) {
							MediaSegmentType.OUTRO -> context.getString(R.string.segment_type_outro)
							MediaSegmentType.INTRO -> context.getString(R.string.segment_type_intro)
                            MediaSegmentType.COMMERCIAL -> context.getString(R.string.segment_type_commercial)
                            MediaSegmentType.PREVIEW -> context.getString(R.string.segment_type_preview)
                            MediaSegmentType.RECAP -> context.getString(R.string.segment_type_recap)
							MediaSegmentType.UNKNOWN -> context.getString(R.string.segment_type_unknown)
                        },
                        value = defaultAction,
                        onValueChange = { action ->
                            setDefaultAction(action)
                            mediaSegmentRepository.setDefaultSegmentTypeAction(segmentType, action)
                        },
                        options = MediaSegmentAction.entries.toList(),
                        optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                        description = context.getString((defaultAction as org.jellyfin.preference.PreferenceEnum).nameRes)
                    )
                }
            }

            // Skip Duration
            item {
                val (skipDuration, setSkipDuration) = rememberEnumPreferenceState(
                    preference = UserPreferences.skipDuration,
                    preferences = userPreferences
                )

                EnumPreference(
                    title = context.getString(R.string.pref_skip_duration),
                    value = skipDuration,
                    onValueChange = setSkipDuration,
                    options = SkipDuration.entries.toList(),
                    optionLabel = { context.getString((it as org.jellyfin.preference.PreferenceEnum).nameRes) },
                    description = context.getString((skipDuration as org.jellyfin.preference.PreferenceEnum).nameRes)
                )
            }

            item {
                PreferenceHeader(context.getString(R.string.Advanced_Settings),)
            }

            // Advanced Playback Settings Link
            item {
                ActionPreference(
                    title = context.getString(R.string.pref_playback_advanced),
                    description = context.getString(R.string.pref_playback_advanced_Description),
                    icon = R.drawable.ic_more,
                    onClick = onNavigateToAdvanced
                )
            }
        }
    }
}

@Composable
private fun SubtitlePreviewPreference(
    textColor: Color,
    backgroundColor: Color,
    textSize: Float,
    isBold: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 0.8.dp,
                color = Color.White,
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Subtitle Preview Example",
            color = textColor,
            fontSize = textSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ColorPreference(
    title: String,
    value: Color,
    onValueChange: (Color) -> Unit,
    options: Map<Color, String>,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val colorOptions = options.map { (color, name) ->
        color to name
    }.toMap()

    // Find the matching color by comparing the full color value (including alpha)
    val selectedKey = colorOptions.keys.firstOrNull { it.value == value.value } ?: value

    ListPreference(
        title = title,
        value = selectedKey.value.toString(),
        onValueChange = { key ->
            val color = colorOptions.keys.firstOrNull { it.value.toString() == key } ?: value
            onValueChange(color)
        },
        options = colorOptions.map { (color, name) -> color.value.toString() to name }.toMap(),
        defaultValue = value.value.toString(),
        description = description ?: (colorOptions[value] ?: "Custom"),
        modifier = modifier
    )
}
