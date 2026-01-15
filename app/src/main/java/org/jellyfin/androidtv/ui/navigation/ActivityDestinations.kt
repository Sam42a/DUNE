package org.jellyfin.androidtv.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import org.jellyfin.androidtv.ui.playback.ExternalPlayerActivity
import org.jellyfin.androidtv.ui.preference.PreferencesComposeActivity
import kotlin.time.Duration

object ActivityDestinations {

	fun userPreferences(context: Context) = Intent(context, PreferencesComposeActivity::class.java)

	fun displayPreferencesCompose(context: Context, displayPreferencesId: String, allowViewSelection: Boolean) =
		Intent(context, PreferencesComposeActivity::class.java).apply {
			putExtra("displayPreferencesId", displayPreferencesId)
			putExtra("allowViewSelection", allowViewSelection)
			putExtra("initialScreen", "display_preferences")
			putExtra("standalone", true)
			putExtra("shouldRefresh", true)
		}

	fun liveTvGuideFilterPreferences(context: Context) = Intent(context, PreferencesComposeActivity::class.java).apply {
		putExtra("initialScreen", "live_tv_guide_filters")
		putExtra("standalone", true)
	}

	fun liveTvGuideOptionPreferences(context: Context) = Intent(context, PreferencesComposeActivity::class.java).apply {
		putExtra("initialScreen", "live_tv_guide_options")
		putExtra("standalone", true)
	}

	fun externalPlayer(context: Context, position: Duration = Duration.ZERO) = Intent(context, ExternalPlayerActivity::class.java).apply {
		putExtras(
			bundleOf(
				ExternalPlayerActivity.EXTRA_POSITION to position.inWholeMilliseconds
			)
		)
	}
}
