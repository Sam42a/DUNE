package org.jellyfin.androidtv.ui.startup

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.JellyfinApplication
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.databinding.ActivityStartupBinding
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.browsing.MainActivity
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.startup.fragment.SelectServerFragment
import org.jellyfin.androidtv.ui.startup.fragment.ServerFragment
import org.jellyfin.androidtv.ui.startup.fragment.SplashFragment
import org.jellyfin.androidtv.ui.startup.fragment.StartupToolbarFragment
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.DeviceUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.UUID

class StartupActivity : FragmentActivity() {
	companion object {
		const val EXTRA_ITEM_ID = "ItemId"
		const val EXTRA_ITEM_IS_USER_VIEW = "ItemIsUserView"
		const val EXTRA_HIDE_SPLASH = "HideSplash"
	}

	private val startupViewModel: StartupViewModel by viewModel()
	private val api: ApiClient by inject()
	private val mediaManager: MediaManager by inject()
	private val sessionRepository: SessionRepository by inject()
	private val userRepository: UserRepository by inject()
	private val navigationRepository: NavigationRepository by inject()
	private val itemLauncher: ItemLauncher by inject()
	private val socketHandler: SocketHandler by inject()

	private lateinit var binding: ActivityStartupBinding

	private val networkPermissionsRequester = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { grants ->
		val requiredPermissions = listOf(
			Manifest.permission.INTERNET,
			Manifest.permission.ACCESS_NETWORK_STATE
		)
		val allRequiredGranted = requiredPermissions.all { grants[it] == true }

		if (!allRequiredGranted) {
			// Required network permissions denied, exit the app.
			Toast.makeText(this, R.string.no_network_permissions, Toast.LENGTH_LONG).show()
			finish()
		} else {
			if (!DeviceUtils.isFireTv(this)) {
				requestOptionalMicrophonePermission()
			} else {
				onPermissionsGranted()
			}
		}
	}

	private val microphonePermissionRequester = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		// Microphone permission is optional, so we don't need to do anything special if denied
		if (isGranted) {
			Timber.d("Microphone permission granted")
		} else {
			Timber.d("Microphone permission denied")
		}
		onPermissionsGranted()
	}

	private fun requestOptionalMicrophonePermission() {
		microphonePermissionRequester.launch(Manifest.permission.RECORD_AUDIO)
	}

	private val handler = Handler(Looper.getMainLooper())
	private val splashDuration = 1000L // 1 second

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		binding = ActivityStartupBinding.inflate(layoutInflater)
		binding.background.setContent { AppBackground() }
		binding.screensaver.isVisible = false
		setContentView(binding.root)

		// Show splash screen
		showSplash()

		// Request required network permissions first
		networkPermissionsRequester.launch(arrayOf(
			Manifest.permission.INTERNET,
			Manifest.permission.ACCESS_NETWORK_STATE
		))
	}

	override fun onResume() {
		super.onResume()

		applyTheme()
	}

	private fun onPermissionsGranted() = sessionRepository.state
		.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
		.filter { it == SessionRepositoryState.READY }
		.map { sessionRepository.currentSession.value }
		.distinctUntilChanged()
		.onEach { session ->
			if (session != null) {
				Timber.i("Found a session in the session repository, waiting for the currentUser in the application class.")

				showSplash()

				val currentUser = userRepository.currentUser.first { it != null }
				Timber.i("CurrentUser changed to ${currentUser?.id} while waiting for startup.")

				lifecycleScope.launch {
					// Ensure WebSocket connection is active before proceeding
					try {
						socketHandler.updateSession()
						Timber.i("WebSocket session updated after login")
					} catch (e: Exception) {
						Timber.e(e, "Failed to update WebSocket session after login")
					}
					openNextActivity()
				}
			} else {
				// Clear audio queue in case left over from last run
				mediaManager.clearAudioQueue()

				val server = startupViewModel.getLastServer()
				if (server != null) showServer(server.id)
				else showServerSelection()
			}
		}.launchIn(lifecycleScope)

	private suspend fun openNextActivity() {
		try {
			// Get the current intent
			val currentIntent = intent
			Timber.d("Processing intent in openNextActivity: $currentIntent")
			Timber.d("Action: ${currentIntent.action}")

			val mainIntent = Intent(this, MainActivity::class.java).apply {
				// Copy all data from the original intent
				intent?.let { original ->
					// Copy action and data
					action = original.action
					data = original.data
					type = original.type

					// Copy categories if any
					original.categories?.forEach { addCategory(it) }

					// Copy all extras
					original.extras?.let { putExtras(it) }

					// Add flags
					flags = original.flags or
						Intent.FLAG_ACTIVITY_SINGLE_TOP or
						Intent.FLAG_ACTIVITY_CLEAR_TOP or
						Intent.FLAG_ACTIVITY_NEW_TASK
				}
			}

			Timber.d("Starting MainActivity with intent: $mainIntent")
			Timber.d("Intent data: ${mainIntent.data}")
			Timber.d("Intent action: ${mainIntent.action}")
			Timber.d("Intent extras: ${mainIntent.extras?.keySet()?.joinToString()}")

			// Start the main activity
			startActivity(mainIntent)

			// Finish this activity
			finishAfterTransition()
		} catch (e: Exception) {
			Timber.e(e, "Error in openNextActivity")
			// Fallback to default behavior
			startActivity(Intent(this, MainActivity::class.java))
			finish()
		}
	}

	// Show splash screen
	private fun showSplash() {
		// Prevent progress bar flashing
		if (isFinishing || isDestroyed) return
		if (supportFragmentManager.findFragmentById(R.id.content_view) is SplashFragment) return

		supportFragmentManager.commit {
			replace<SplashFragment>(R.id.content_view)
		}

		// Automatically hide splash after delay
		handler.postDelayed({
			if (!isFinishing && !isDestroyed) {
				try {
					supportFragmentManager.popBackStack()
				} catch (e: IllegalStateException) {
					// Ignore if fragment manager is already destroyed
				}
			}
		}, splashDuration)
	}

	private fun showServer(id: UUID) = supportFragmentManager.commit {
		replace<StartupToolbarFragment>(R.id.content_view)
		add<ServerFragment>(
			R.id.content_view, null, bundleOf(
				ServerFragment.ARG_SERVER_ID to id.toString()
			)
		)
	}

	private fun showServerSelection() = supportFragmentManager.commit {
		replace<StartupToolbarFragment>(R.id.content_view)
		add<SelectServerFragment>(R.id.content_view)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
	}
}
