package org.jellyfin.androidtv.ui.preference

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.jellyfin.androidtv.R

class ClockPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.clock_preferences, rootKey)
    }

    companion object {
        const val PREF_SHOW_WHITE_BORDER = "pref_show_white_border"
    }
}
