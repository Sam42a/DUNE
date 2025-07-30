package org.jellyfin.androidtv.ui.preference

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.screen.UserPreferencesScreen

class PreferencesActivity : FragmentActivity(R.layout.fragment_content_view) {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the theme before super.onCreate
        setTheme(R.style.Theme_Jellyfin_Preferences)
        
        // Make the window transparent
        window.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = Color.TRANSPARENT
                decorView.systemUiVisibility = 
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        }
        
        super.onCreate(savedInstanceState)

        val screen = intent.extras?.getString(EXTRA_SCREEN) ?: UserPreferencesScreen::class.qualifiedName
        val screenArgs = intent.extras?.getBundle(EXTRA_SCREEN_ARGS) ?: bundleOf()

        if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.content_view, PreferencesFragment().apply {
                    arguments = bundleOf(
                        PreferencesFragment.EXTRA_SCREEN to screen,
                        PreferencesFragment.EXTRA_SCREEN_ARGS to screenArgs
                    )
                }, FRAGMENT_TAG)
                .commit()
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_SCREEN_ARGS = "screen_args"
        const val FRAGMENT_TAG = "PreferencesActivity"
    }
}

