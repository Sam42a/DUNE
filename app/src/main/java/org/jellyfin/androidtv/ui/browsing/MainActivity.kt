package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.app.SearchManager
import android.provider.MediaStore
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.databinding.ActivityMainBinding
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.ScreensaverViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.isMediaSessionKeyEvent
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.UUID
import kotlin.math.min

class MainActivity : FragmentActivity() {
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val screensaverViewModel by viewModel<ScreensaverViewModel>()
	private val workManager by inject<WorkManager>()
	private val socketHandler by inject<SocketHandler>()

	private lateinit var binding: ActivityMainBinding

	private var isAtRoot = true
	private val backPressedCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			if (navigationRepository.canGoBack) {
				navigationRepository.goBack()
				isAtRoot = !navigationRepository.canGoBack
			} else if (isAtRoot) {
				showExitConfirmation()
			} else {
				// Shouldn't normally get here, but just in case
				finish()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		// Initialize view binding first
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!validateAuthentication()) return

		// Setup screensaver and background
		binding.background.setContent { AppBackground() }
		binding.screensaver.setContent { InAppScreensaver() }

		// Setup screen on/off state observer
		screensaverViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		// Setup back press handler
		onBackPressedDispatcher.addCallback(this, backPressedCallback)
		if (savedInstanceState == null) {
			navigationRepository.reset(clearHistory = true)
		}
		isAtRoot = !navigationRepository.canGoBack

		// Setup navigation
		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { action ->
				handleNavigationAction(action)
				isAtRoot = !navigationRepository.canGoBack
				screensaverViewModel.notifyInteraction(false)
			}.launchIn(lifecycleScope)

		// Trigger initial navigation if this is a fresh start
		if (savedInstanceState == null) {
			navigationRepository.reset(clearHistory = true)
		}
	}

	override fun onNewIntent(intent: Intent) {
			super.onNewIntent(intent)
			setIntent(intent) // Important: Update the intent so getIntent() returns the latest one
			Timber.d("onNewIntent: ${intent.data}")
			Timber.d("Intent action: ${intent.action}")
			Timber.d("Intent extras: ${intent.extras?.keySet()?.joinToString()}")

			// Handle the intent in onResume to ensure the activity is fully created
			handleIntent(intent)
		}

		private fun handleIntent(intent: Intent) {
			try {
				// Check for item ID in extras
				val itemId = intent.getStringExtra("ItemId") ?: return
				val isUserView = intent.getBooleanExtra("ItemIsUserView", false)
				val searchQuery = if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
					intent.getStringExtra(SearchManager.QUERY) ?: ""
				} else null
				val startPlayback = !searchQuery.isNullOrBlank()

				Timber.d("Handling intent with item: $itemId, isUserView: $isUserView, startPlayback: $startPlayback, searchQuery: $searchQuery")

				// Create a minimal BaseItemDto with required fields
				val item = BaseItemDto(
					id = UUID.fromString(itemId),
					type = if (isUserView) BaseItemKind.USER_VIEW else BaseItemKind.FOLDER,
					mediaType = MediaType.UNKNOWN,
					name = ""
				)

				// Reset navigation first to ensure clean state
				navigationRepository.reset(clearHistory = true)

				// Use itemDetails for navigation as it's more reliable with minimal item data
				val destination = Destinations.itemDetails(item.id!!)
				navigationRepository.navigate(destination)

				// Clear the intent to prevent handling it multiple times
				setIntent(Intent())
			} catch (e: Exception) {
				Timber.e(e, "Error handling intent navigation")
			}
		}

		override fun onResume() {
			super.onResume()

			if (!validateAuthentication()) return

			applyTheme()

			screensaverViewModel.activityPaused = false

			// Ensure WebSocket connection is active
			lifecycleScope.launch(Dispatchers.IO) {
				try {
					socketHandler.updateSession()
					Timber.i("WebSocket session updated in MainActivity.onResume")
				} catch (e: Exception) {
					Timber.e(e, "Failed to update WebSocket session in MainActivity.onResume")
				}
			}

			// Handle any pending intents when the activity is resumed
			intent?.let { currentIntent ->
				if (currentIntent.hasExtra("ItemId")) {
					handleIntent(currentIntent)
				}
			}
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

	override fun onPause() {
		super.onPause()

		screensaverViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		// Only clear session if the activity is finishing
		if (isFinishing) {
			lifecycleScope.launch(Dispatchers.IO) {
				Timber.d("MainActivity finishing, cleaning up session")
				sessionRepository.restoreSession(destroyOnly = true)
			}
		} else {
			Timber.d("MainActivity stopped but not finishing, keeping session")
		}
	}

	private fun handleNavigationAction(action: NavigationAction) {
		screensaverViewModel.notifyInteraction(true)

		when (action) {
			is NavigationAction.NavigateFragment -> binding.contentView.navigate(action)
			NavigationAction.GoBack -> binding.contentView.goBack()

			NavigationAction.Nothing -> Unit
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

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

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
            setPositiveButton(R.string.yes) { _, _ -> finishAndRemoveTask() }
            setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
            setCancelable(true)
        }

        val alertDialog = dialog.create()
        val displayMetrics = applicationContext.resources.displayMetrics
        val screenWidth = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

        alertDialog.window?.let { window ->
			// Set the background drawable
			val drawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.exit_dialog_background)
			window.setBackgroundDrawable(drawable)

			// Set dialog width to 35% of screen width for a more compact look
			val params = WindowManager.LayoutParams().apply {
				copyFrom(window.attributes)
				width = (screenWidth * 0.35).toInt()
				height = WindowManager.LayoutParams.WRAP_CONTENT
				gravity = Gravity.CENTER
			}
			window.attributes = params
		}

		alertDialog.show()

		// Style the message with proper spacing and larger text
		val messageView = alertDialog.findViewById<android.widget.TextView>(android.R.id.message)
		messageView?.apply {
			gravity = Gravity.CENTER
			setTextColor(Color.WHITE)
			textSize = 16f  // Increased from 14f to 16f for better readability
			// Add more bottom padding to create space above buttons
			setPadding(24, 24, 24, 32)
		}

		try {
			val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
			val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

			// Set both buttons to white
			val whiteColor = Color.WHITE
			positiveButton?.setTextColor(whiteColor)
			negativeButton?.setTextColor(whiteColor)

			// Set padding for buttons with more vertical padding
			val buttonPadding = (12 * applicationContext.resources.displayMetrics.density).toInt()
			val buttonPaddingVertical = (10 * applicationContext.resources.displayMetrics.density).toInt()
			positiveButton?.setPadding(buttonPadding, buttonPaddingVertical, buttonPadding, buttonPaddingVertical)
			negativeButton?.setPadding(buttonPadding, buttonPaddingVertical, buttonPadding, buttonPaddingVertical)

			// Add margin between buttons
			val buttonMargin = (8 * resources.displayMetrics.density).toInt()
			(positiveButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = buttonMargin / 2
			(negativeButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = buttonMargin / 2

			// Center the buttons in their container
			(negativeButton?.parent as? LinearLayout)?.apply {
				gravity = Gravity.CENTER_HORIZONTAL
				orientation = LinearLayout.HORIZONTAL
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

    private fun handleOnBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            showExitConfirmation()
        }
    }
}
