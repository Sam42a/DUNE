package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.databinding.FragmentHomeBinding
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.ImagePreloader
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID
import kotlin.math.min

// Removed interface as we'll use direct field access

class HomeFragment : Fragment() {
    private val api: ApiClient by inject()
    private val imageHelper: ImageHelper by inject()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @JvmField
    var isReadyForInteraction = false

    private val sessionRepository by inject<SessionRepository>()
    private val userRepository by inject<UserRepository>()
    private val serverRepository by inject<ServerRepository>()
    private val notificationRepository by inject<NotificationsRepository>()
    private val navigationRepository by inject<NavigationRepository>()
    private val mediaManager by inject<MediaManager>()
    private val playbackLauncher: PlaybackLauncher by inject()
    private val userSettingPreferences: UserSettingPreferences by inject()
    private val userPreferences: UserPreferences by inject()
    private val imagePreloader: ImagePreloader by inject()
    private val backgroundService by inject<org.jellyfin.androidtv.data.service.BackgroundService>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        isReadyForInteraction = true

        // Start preloading images when the fragment resumes
        preloadHomeScreenImages()

        // Clear backdrop when navigating to home
        try {
            backgroundService.clearBackgrounds()
        } catch (e: Exception) {
			Timber.tag("HomeFragment").e(e, "Error clearing backdrop")
        }
    }

    override fun onDestroyView() {
        try {
            // Cancel any ongoing work
            view?.let { v ->
                if (v.isAttachedToWindow) {
                    v.viewTreeObserver.removeOnWindowFocusChangeListener { /* no-op */ }
                    v.removeCallbacks(null)
                    v.setOnClickListener(null)
                    v.setOnKeyListener(null)
                }
            }

            // Clear coroutine jobs if view is still available
            if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                try {
                    viewLifecycleOwner.lifecycleScope.coroutineContext[Job]?.cancel()
                } catch (e: Exception) {
                    Timber.e(e, "Error clearing coroutine jobs")
                }
            }

            // Clear binding
            _binding = null
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroyView")
        } finally {
            super.onDestroyView()
        }
    }

    override fun onDestroy() {
        try {
            // Clear any remaining references
            clearReferences()

            // Clear fragment transactions if not in state saving
            if (!isRemoving && !isStateSaved) {
                try {
                    parentFragmentManager
                        .beginTransaction()
                        .remove(this)
                        .commitAllowingStateLoss()
                } catch (e: Exception) {
                    Timber.e(e, "Error removing fragment in onDestroy")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroy")
        } finally {
            super.onDestroy()
        }
    }

    /**
     * Clear any remaining references to prevent memory leaks
     */
    private fun clearReferences() {
        try {
            // Clear any remaining listeners or callbacks
            // Add any additional cleanup needed for your fragment

            // Clear coroutine jobs if view is still available
            if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                try {
                    viewLifecycleOwner.lifecycleScope.coroutineContext[Job]?.cancel()
                } catch (e: Exception) {
                    Timber.e(e, "Error clearing coroutine jobs")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in clearReferences")
        }
    }

    private fun preloadHomeScreenImages() {
        if (!userPreferences[UserPreferences.preloadImages]) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get the first 20 items from each row for preloading
                val urls = mutableListOf<String>()

                // Get all fragments that might contain rows
                childFragmentManager.fragments.forEach { fragment ->
                    try {
                        // Try to get rows using reflection
                        val rowsField = fragment.javaClass.declaredFields
                            .firstOrNull { it.name == "rows" }
                            ?.apply { isAccessible = true }

                        @Suppress("UNCHECKED_CAST")
                        val rows = rowsField?.get(fragment) as? List<Any> ?: return@forEach

                        rows.forEach { row ->
                            try {
                                // Try to get the adapter
                                val adapterField = row.javaClass.declaredFields
                                    .firstOrNull { it.name == "adapter" }
                                    ?.apply { isAccessible = true }

                                val adapter = adapterField?.get(row) as? androidx.leanback.widget.ItemBridgeAdapter
                                    ?: return@forEach

                                // Get the wrapped adapter using getWrapper() method
                                val wrapperMethod = adapter.javaClass.getMethod("getWrapper")
                                val wrapper = wrapperMethod.invoke(adapter) as? androidx.leanback.widget.ObjectAdapter
                                    ?: return@forEach

                                // Get items safely
                                val count = min(20, wrapper.size())
                                for (i in 0 until count) {
                                    try {
                                        val item = wrapper.get(i)
                                        // Example: if (item is BaseRowItem) item.imageUrl?.let { urls.add(it) }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error getting item at index $i")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error accessing row adapter")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error accessing rows")
                    }
                }

                // Preload the collected URLs if we have any
                if (urls.isNotEmpty()) {
                    imagePreloader.preloadImages(requireContext(), urls)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in preloadHomeScreenImages")
            }
        }
    }

    // Removed onAttach and onDetach as they're no longer needed with direct field access

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initially block interactions
        view.isFocusableInTouchMode = false
        view.isClickable = false

        // Set a delay to enable interactions after initial load
        view.postDelayed({
            isReadyForInteraction = true
            view.isFocusableInTouchMode = true
            view.isClickable = true
        }, 1000) // 1 second delay, adjust based on your needs

        binding.toolbar.setContent {
            val searchAction = {
                // Navigate to search screen
                navigationRepository.navigate(Destinations.search())
            }
            val settingsAction = {
                // Open preferences/settings activity
                val intent = Intent(requireContext(), org.jellyfin.androidtv.ui.preference.PreferencesActivity::class.java)
                startActivity(intent)
            }
            val switchUsersAction = {
                switchUser()
            }

            val liveTvAction = {
    val lastChannelId = org.jellyfin.androidtv.ui.livetv.TvManager.getLastLiveTvChannel()
    if (lastChannelId != null) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val channel = withContext(Dispatchers.IO) {
                    api.liveTvApi.getChannel(lastChannelId).content
                }
                // Launch playback for the channel
                playbackLauncher.launch(requireContext(), listOf(channel), 0, false, 0, false)
            } catch (e: Exception) {
                // If fetch fails, fallback to guide
                navigationRepository.navigate(Destinations.liveTvGuide)
            }
        }
    } else {
        navigationRepository.navigate(Destinations.liveTvGuide)
    }
}
            val libraryAction = {
                // Navigate to the home screen which shows the library content
                navigationRepository.navigate(Destinations.home)
            }

            val favoritesAction = {
                // Navigate to the favorites fragment
                navigationRepository.navigate(Destinations.favorites)
            }

            val openRandomMovie: (BaseItemDto) -> Unit = { item ->
                item.id?.let { idStr ->
                    try {
                        val uuid = UUID.fromString(idStr.toString())
                        navigationRepository.navigate(Destinations.itemDetails(uuid))
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing item ID")
                    }
                }
            }

            org.jellyfin.androidtv.ui.shared.toolbar.HomeToolbar(
                openSearch = { searchAction() },
                openLiveTv = { liveTvAction() },
                openSettings = { settingsAction() },
                switchUsers = { switchUsersAction() },
                openLibrary = { libraryAction() },
                onFavoritesClick = { favoritesAction() },
                openRandomMovie = openRandomMovie,
                userSettingPreferences = userSettingPreferences
            )
        }
    }

    private fun switchUser() {
        if (!isReadyForInteraction) return

        mediaManager.clearAudioQueue()
        sessionRepository.destroyCurrentSession()

        val selectUserIntent = Intent(activity, StartupActivity::class.java)
        selectUserIntent.putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)
        selectUserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

        activity?.startActivity(selectUserIntent)
        activity?.finishAfterTransition()
    }

    private fun openItemDetails(item: org.jellyfin.sdk.model.api.BaseItemDto) {
        item.id?.let { idStr ->
            val uuid = try {
                UUID.fromString(idStr.toString())
            } catch (e: Exception) {
                null
            }
            if (uuid != null) {
                navigationRepository.navigate(Destinations.itemDetails(uuid)) // itemDetails expects UUID

            }
        }
    }
}
