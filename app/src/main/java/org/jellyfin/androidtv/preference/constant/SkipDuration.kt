package org.jellyfin.androidtv.preference.constant

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Skip duration options for the skip overlay visibility timer
 */
enum class SkipDuration(
    val duration: Duration,
    override val nameRes: Int
) : PreferenceEnum {
    DEFAULT_8_SECONDS(8.seconds, R.string.skip_duration_default),
    TEN_SECONDS(10.seconds, R.string.skip_duration_10_seconds),
    FIFTEEN_SECONDS(15.seconds, R.string.skip_duration_15_seconds),
    TWENTY_SECONDS(20.seconds, R.string.skip_duration_20_seconds);

    companion object {
        fun fromDuration(duration: Duration): SkipDuration {
            return entries.find { it.duration == duration } ?: DEFAULT_8_SECONDS
        }
    }
}
