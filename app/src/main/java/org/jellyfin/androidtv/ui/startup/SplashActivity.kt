package org.jellyfin.androidtv.ui.startup

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.ActivitySplashBinding
import org.jellyfin.androidtv.ui.browsing.MainActivity
import timber.log.Timber

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    // Reduced splash duration to 1 second for faster app start
    private val splashDuration = 1000L // 1 second

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The logo is already set in the layout XML
        // Just schedule the transition to MainActivity
        handler.postDelayed({
            if (!isFinishing) {
                startMainActivity()
            }
        }, splashDuration)
    }

    private fun startMainActivity() {
        // First try to start MainActivity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            // Add any necessary flags or extras
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        // Also start StartupActivity to handle authentication if needed
        val startupIntent = Intent(this, StartupActivity::class.java).apply {
            putExtra("fromSplash", true)
        }
        
        // Start both activities
        startActivities(arrayOf(mainIntent, startupIntent))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
