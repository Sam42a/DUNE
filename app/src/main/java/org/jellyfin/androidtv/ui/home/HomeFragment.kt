package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
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
import org.jellyfin.androidtv.ui.AsyncImageView
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

class HomeFragment : Fragment() {
    private val api: ApiClient by inject()
    private val imageHelper: ImageHelper by inject()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val scrollListeners = mutableListOf<RecyclerView.OnScrollListener>()
    private var isScrolling = false
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var scrollCheckRunnable: Runnable? = null
    private val scrollCheckDelay = 350L // ms to wait after scroll stops
    private var interactionDelayRunnable: Runnable? = null

    /**
     * Set up scroll listeners for all RecyclerViews in the fragment
     */
    private fun setupScrollListeners() {
        // Check if binding is still valid (fragment view not destroyed)
        val binding = _binding ?: return

        binding.root.post {
            val currentBinding = _binding ?: return@post

            findRecyclerViews(currentBinding.root).forEach { recyclerView ->
                val scrollListener = object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)

                        when (newState) {
                            RecyclerView.SCROLL_STATE_DRAGGING,
                            RecyclerView.SCROLL_STATE_SETTLING -> {
                                // Started scrolling
                                setScrolling(true)
                            }
                            RecyclerView.SCROLL_STATE_IDLE -> {
                                // Stopped scrolling
                                postScrollCheck()
                            }
                        }
                    }
                }

                recyclerView.addOnScrollListener(scrollListener)
                scrollListeners.add(scrollListener)
            }
        }
    }

    private fun findRecyclerViews(viewGroup: ViewGroup): List<RecyclerView> {
        val recyclerViews = mutableListOf<RecyclerView>()

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is RecyclerView -> recyclerViews.add(child)
                is ViewGroup -> recyclerViews.addAll(findRecyclerViews(child))
            }
        }

        return recyclerViews
    }

    private fun setScrolling(scrolling: Boolean) {
        if (isScrolling != scrolling) {
            isScrolling = scrolling

            // Notify all AsyncImageViews in the fragment
            notifyAsyncImageViewsOfScrollState(scrolling)
        }
    }
    private fun postScrollCheck() {
        scrollCheckRunnable?.let {
            scrollHandler.removeCallbacks(it)
        }

        scrollCheckRunnable = Runnable {
            setScrolling(false)
            scrollCheckRunnable = null
        }

        scrollHandler.postDelayed(scrollCheckRunnable!!, scrollCheckDelay)
    }

    private fun notifyAsyncImageViewsOfScrollState(scrolling: Boolean) {
        // Check if binding is still valid (fragment view not destroyed)
        val binding = _binding ?: return

        binding.root.post {
            // Check binding again in case fragment was destroyed during post delay
            val currentBinding = _binding ?: return@post

            findAsyncImageViews(currentBinding.root).forEach { asyncImageView ->
                asyncImageView.setScrollState(scrolling)
            }
        }
    }
    private fun findAsyncImageViews(viewGroup: ViewGroup): List<AsyncImageView> {
        val asyncImageViews = mutableListOf<AsyncImageView>()

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is AsyncImageView -> asyncImageViews.add(child)
                is ViewGroup -> asyncImageViews.addAll(findAsyncImageViews(child))
            }
        }

        return asyncImageViews
    }

    /**
     * Clean up scroll listeners
     */
    private fun cleanupScrollListeners() {
        // Check if binding is still valid (fragment view not destroyed)
        val binding = _binding ?: return

        scrollListeners.forEach { listener ->
            // Remove listener from all RecyclerViews
            findRecyclerViews(binding.root).forEach { recyclerView ->
                recyclerView.removeOnScrollListener(listener)
            }
        }
        scrollListeners.clear()

        scrollCheckRunnable?.let {
            scrollHandler.removeCallbacks(it)
            scrollCheckRunnable = null
        }
    }

    @JvmField
    var isReadyForInteraction = false

    private val sessionRepository by inject<SessionRepository>()
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

            if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                try {
                    viewLifecycleOwner.lifecycleScope.coroutineContext[Job]?.cancel()
                } catch (e: Exception) {
                    Timber.e(e, "Error clearing coroutine jobs")
                }
            }

            // Clean up scroll listeners
            cleanupScrollListeners()

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
            // Clean up scroll handler and runnables
            scrollCheckRunnable?.let {
                scrollHandler.removeCallbacks(it)
                scrollCheckRunnable = null
            }

            // Clean up interaction delay runnable
            interactionDelayRunnable?.let {
                view?.removeCallbacks(it)
                interactionDelayRunnable = null
            }

            // Clean up viewTreeObserver
            view?.let { v ->
                if (v.isAttachedToWindow) {
                    v.viewTreeObserver.removeOnWindowFocusChangeListener { /* no-op */ }
                }
            }

            // Clean up scroll listeners
            cleanupScrollListeners()

            // Clear any remaining listeners or callbacks
            view?.let { v ->
                v.setOnClickListener(null)
                v.setOnKeyListener(null)
                v.removeCallbacks(null)
            }

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

        // Check if fragment is still in a valid state
        if (!isAdded || view == null || activity == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isReadyForInteraction) return@launch

                if (!isAdded || view == null || activity == null) return@launch

                // a lightweight approach - collect URLs from visible rows only
                val urls = mutableListOf<String>()

                // Get only the first few visible fragments instead of all fragments
                val visibleFragments = try {
                    childFragmentManager.fragments.take(3)
                } catch (e: Exception) {
                    Timber.e(e, "Error getting child fragments")
                    return@launch
                }

                visibleFragments.forEach { fragment ->
                    try {
                        if (fragment::class.java.simpleName.contains("Row")) {
                            // The AsyncImageView optimizations will handle the rest
                            return@forEach
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking fragment type")
                    }
                }

                // Only preload if we have URLs and the system is not under heavy load
                if (urls.isNotEmpty()) {
                    try {
                        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memoryInfo = ActivityManager.MemoryInfo()
                        activityManager.getMemoryInfo(memoryInfo)

                        // Only preload if we have sufficient memory available
                        if (memoryInfo.availMem > memoryInfo.totalMem * 0.3) {
                            // Check if context is still valid
                            if (isAdded && context != null) {
                                imagePreloader.preloadImages(requireContext(), urls)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking memory or preloading images")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in preloadHomeScreenImages")
            }
        }
    }

    // Removed onAttach and onDetach as they're no longer needed with direct field access

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.isFocusableInTouchMode = false
        view.isClickable = false

        interactionDelayRunnable = Runnable {
            isReadyForInteraction = true
            view.isFocusableInTouchMode = true
            view.isClickable = true

            setupScrollListeners()
        }
        view.postDelayed(interactionDelayRunnable!!, 2000) // 2 second delay

        binding.toolbar.setContent {
            val searchAction = {
                navigationRepository.navigate(Destinations.search())
            }
            val settingsAction = {
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
