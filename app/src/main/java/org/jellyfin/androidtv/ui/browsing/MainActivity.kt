package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.jellyfin.androidtv.R
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.databinding.ActivityMainBinding
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.ScreensaverViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.browsing.DestinationFragmentView
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.isMediaSessionKeyEvent
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : FragmentActivity() {
	private var backPressedTime: Long = 0
	private val BACK_PRESS_DELAY = 2000 // 2 seconds

	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val screensaverViewModel by viewModel<ScreensaverViewModel>()
	private val workManager by inject<WorkManager>()

	private lateinit var binding: ActivityMainBinding

	private val backPressedCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			if (navigationRepository.canGoBack) navigationRepository.goBack()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		// Apply theme before super to ensure proper theme setup
		super.onCreate(savedInstanceState)
		applyTheme()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!validateAuthentication()) return

		// Initialize background and screensaver
		binding.background.setContent { AppBackground() }
		binding.screensaver.setContent { InAppScreensaver() }

		// Set up screen keep-on
		screensaverViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		// Set up back press handling
		onBackPressedDispatcher.addCallback(this, backPressedCallback)

		// Initialize navigation
		if (savedInstanceState == null) {
			if (navigationRepository.canGoBack) {
				navigationRepository.reset(clearHistory = true)
			} else {
				// If no saved state and no back stack, navigate to home
				navigationRepository.navigate(Destinations.home, true)
			}
		} else if (!navigationRepository.canGoBack) {
			// If we're being restored but have no back stack, ensure we're at home
			navigationRepository.reset(Destinations.home, true)
		}

		// Observe navigation actions
		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { action ->
				if (action != null) {
					handleNavigationAction(action)
					backPressedCallback.isEnabled = navigationRepository.canGoBack
					screensaverViewModel.notifyInteraction(false)
				}
			}.launchIn(lifecycleScope)
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		// Only apply theme if we're not in the middle of a configuration change
		if (!isChangingConfigurations) {
			applyTheme()
		}

		screensaverViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	private fun handleNavigationAction(action: NavigationAction) {
		if (isDestroyed || isFinishing) return

		try {
			screensaverViewModel.notifyInteraction(true)

			when (action) {
				is NavigationAction.NavigateFragment -> {
					if (!isDestroyed && !isFinishing) {
						Timber.d("Navigating to fragment: ${action.destination.fragment}")
						binding.contentView.navigate(action)
					}
				}
				NavigationAction.GoBack -> {
					if (!isDestroyed && !isFinishing) {
						Timber.d("Going back in fragment stack")
						binding.contentView.goBack()
					}
				}
				NavigationAction.Nothing -> {
					Timber.d("Received NavigationAction.Nothing")
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "Error handling navigation action")
		}
	}

	// Forward key events to fragments
	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		// Ignore the key event that closes the screensaver
		if (screensaverViewModel.visible.value) {
			screensaverViewModel.notifyInteraction(canCancel = event?.action == KeyEvent.ACTION_UP)
			return true
		}

		return supportFragmentManager.fragments
			.any { it.onKeyEvent(keyCode, event) }
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// Check if we're on the home screen by checking back stack and current fragment
			val currentFragment = supportFragmentManager.fragments.lastOrNull()
			val isOnHomeScreen = currentFragment is HomeFragment || 
					(supportFragmentManager.backStackEntryCount == 0 && 
					 supportFragmentManager.primaryNavigationFragment is HomeFragment)

			Timber.d("Back button pressed. isOnHomeScreen: $isOnHomeScreen, backStackCount: ${supportFragmentManager.backStackEntryCount}")

			if (supportFragmentManager.backStackEntryCount > 0) {
				// Let the fragment handle the back press if there are fragments in the back stack
				Timber.d("Back stack not empty, letting fragment handle back press")
				return onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)
			} else if (isOnHomeScreen) {
				// Only show exit confirmation when on the home screen
				Timber.d("On home screen, showing exit confirmation")
				showExitConfirmation()
				return true
			}
			Timber.d("Not on home screen and no fragments in back stack, letting system handle back press")
			// If not on home screen and no fragments in back stack, let the system handle it
			return super.onKeyDown(keyCode, event)
		}
		return onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onUserInteraction() {
		super.onUserInteraction()

		screensaverViewModel.notifyInteraction(false)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && screensaverViewModel.visible.value) {
			screensaverViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyEvent(event)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && screensaverViewModel.visible.value) {
			screensaverViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyShortcutEvent(event)
	}

	override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
		// Ignore the touch event that closes the screensaver
		if (screensaverViewModel.visible.value) {
			screensaverViewModel.notifyInteraction(true)
			return true
		}

		return super.dispatchTouchEvent(ev)
	}

	private fun showExitConfirmation() {
		val dialog = AlertDialog.Builder(this, R.style.ExitDialogTheme).apply {
			setMessage(R.string.exit_app_message)
			setPositiveButton(R.string.yes) { _, _ -> finishAffinity() }
			setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
			setCancelable(true)
		}

		val alertDialog = dialog.create()
		val displayMetrics = resources.displayMetrics
		val screenWidth = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

		alertDialog.window?.let { window ->
			// Set the background drawable
			val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.exit_dialog_background)
			window.setBackgroundDrawable(drawable)
			
			// Set dialog width to 35% of screen width for a more compact look
			val params = android.view.WindowManager.LayoutParams().apply {
				copyFrom(window.attributes)
				width = (screenWidth * 0.35).toInt()
				height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
				gravity = android.view.Gravity.CENTER
			}
			window.attributes = params
		}

		alertDialog.show()

		// Style the message with proper spacing and larger text
		val messageView = alertDialog.findViewById<android.widget.TextView>(android.R.id.message)
		messageView?.apply {
			gravity = android.view.Gravity.CENTER
			setTextColor(android.graphics.Color.WHITE)
			textSize = 16f  // Increased from 14f to 16f for better readability
			// Add more bottom padding to create space above buttons
			setPadding(24, 24, 24, 32)
		}

		try {
			val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
			val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
			
			// Set both buttons to white
			val whiteColor = android.graphics.Color.WHITE
			positiveButton?.setTextColor(whiteColor)
			negativeButton?.setTextColor(whiteColor)

			// Set padding for buttons with more vertical padding
			val buttonPadding = (12 * resources.displayMetrics.density).toInt()
			val buttonPaddingVertical = (10 * resources.displayMetrics.density).toInt()
			positiveButton?.setPadding(buttonPadding, buttonPaddingVertical, buttonPadding, buttonPaddingVertical)
			negativeButton?.setPadding(buttonPadding, buttonPaddingVertical, buttonPadding, buttonPaddingVertical)
			
			// Add margin between buttons
			val buttonMargin = (8 * resources.displayMetrics.density).toInt()
			(positiveButton?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginEnd = buttonMargin / 2
			(negativeButton?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginStart = buttonMargin / 2
			
			// Center the buttons in their container
			(negativeButton?.parent as? android.widget.LinearLayout)?.apply {
				gravity = android.view.Gravity.CENTER_HORIZONTAL
				orientation = android.widget.LinearLayout.HORIZONTAL
			}
			
			// Ensure buttons are focusable
			positiveButton?.isFocusable = true
			negativeButton?.isFocusable = true
			
			// Request focus on the negative button by default
			negativeButton?.requestFocus()
		} catch (e: Exception) {
			Timber.e(e, "Error styling exit dialog")
		}
	}
}
