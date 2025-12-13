package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.databinding.FragmentHomeBinding
import org.jellyfin.androidtv.databinding.FragmentHomeClassicBinding
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
import org.jellyfin.androidtv.ui.home.carousel.CarouselViewModel
import org.jellyfin.androidtv.ui.home.carousel.FeaturedCarousel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.androidtv.ui.home.carousel.CarouselUiState
import timber.log.Timber
import java.util.UUID

class HomeFragment : Fragment() {
    private val api: ApiClient by inject()
    private val imageHelper: ImageHelper by inject()
    private val carouselViewModel: CarouselViewModel by viewModel()
    private var _modernBinding: FragmentHomeBinding? = null
    private var _classicBinding: FragmentHomeClassicBinding? = null
    private val modernBinding get() = _modernBinding!!
    private val classicBinding get() = _classicBinding!!
    private var useClassicLayout = false
    private val currentBinding get() = if (useClassicLayout) _classicBinding else _modernBinding
    private val scrollListeners = mutableListOf<RecyclerView.OnScrollListener>()
    private var isScrolling = false
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var scrollCheckRunnable: Runnable? = null
    private val scrollCheckDelay = 350L // ms to wait after scroll stops
    private var interactionDelayRunnable: Runnable? = null
    private var _isCarouselPaused = mutableStateOf(false)
    val isCarouselPaused: Boolean get() = _isCarouselPaused.value

    /**
     * Set up scroll listeners for all RecyclerViews in the fragment
     */
    private fun setupScrollListeners() {
        val currentBinding = currentBinding ?: return

        currentBinding.root.post {

            findRecyclerViews(currentBinding.root as ViewGroup).forEach { recyclerView ->
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
        val currentBinding = currentBinding ?: return

        currentBinding.root.post {
            val currentBinding = currentBinding ?: return@post

            findAsyncImageViews(currentBinding.root as ViewGroup).forEach { asyncImageView ->
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
        val currentBinding = currentBinding ?: return

        scrollListeners.forEach { listener ->
            // Remove listener from all RecyclerViews
            findRecyclerViews(currentBinding.root as ViewGroup).forEach { recyclerView ->
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
        useClassicLayout = userSettingPreferences.get(userSettingPreferences.useClassicHomeScreen)

        return if (useClassicLayout) {
            _classicBinding = FragmentHomeClassicBinding.inflate(inflater, container, false)
            classicBinding.root
        } else {
            _modernBinding = FragmentHomeBinding.inflate(inflater, container, false)
            modernBinding.root
        }
    }

    override fun onResume() {
        super.onResume()
        isReadyForInteraction = true

        _isCarouselPaused.value = false
        Timber.tag("HomeFragment").d("onResume called - Carousel resumed, isCarouselPaused: ${_isCarouselPaused.value}")

        // Start preloading images when the fragment resumes
        preloadHomeScreenImages()

        refreshBackgroundState()
    }

    override fun onPause() {
        super.onPause()

        _isCarouselPaused.value = true
        Timber.tag("HomeFragment").d("onPause called - Carousel paused, isCarouselPaused: ${_isCarouselPaused.value}")

        try {
            backgroundService.unblockAllBackgrounds() // Unblock for other screens
            Timber.tag("HomeFragment").d("Backgrounds unblocked on pause")
        } catch (e: Exception) {
			Timber.tag("HomeFragment").e(e, "Error unblocking background service on pause")
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
            _modernBinding = null
            _classicBinding = null
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

        //  toolbar for both classic and modern layouts
        val toolbarBinding = if (useClassicLayout) classicBinding.toolbar else modernBinding.toolbar
        toolbarBinding.setContent {
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

        if (!useClassicLayout) {
            modernBinding.carouselContainer?.setContent {
            val carouselUiState by carouselViewModel.uiState.collectAsState()

            when (val state = carouselUiState) {
                is CarouselUiState.Loading -> {
                    // Loading state  could show a shimmer or placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                is CarouselUiState.Success -> {
                    FeaturedCarousel(
                        items = state.items,
                        onItemSelected = { item ->
                            // Navigate to item details
                            try {
                                val uuid = java.util.UUID.fromString(item.id)
                                navigationRepository.navigate(Destinations.itemDetails(uuid))
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing item ID for navigation: ${item.id}")
                            }
                        },
                        isPaused = isCarouselPaused
                    )

                    // Debug logging to track isPaused state
                    Timber.tag("HomeFragment").d("FeaturedCarousel rendered with isPaused: $isCarouselPaused")
                }
                is CarouselUiState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            // Move focus to toolbar when down key is pressed
                                            toolbarBinding.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Color.Transparent.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "No featured content available",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is CarouselUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.Text(
                                text = "Error loading featured content",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error
                            )

                            androidx.compose.material3.TextButton(
                                onClick = { carouselViewModel.refresh() },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                androidx.compose.material3.Text("Retry")
                            }
                        }
                    }
                }
            }
            }
        }
    }

    private fun refreshBackgroundState() {
        try {
            if (useClassicLayout) {
                backgroundService.unblockAllBackgrounds()
                Timber.tag("HomeFragment").d("Backgrounds enabled for classic home screen (refresh)")
            } else {
                backgroundService.blockAllBackgrounds()
                Timber.tag("HomeFragment").d("Backgrounds blocked for modern home screen (refresh)")
            }
        } catch (e: Exception) {
            Timber.tag("HomeFragment").e(e, "Error refreshing background service")
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
