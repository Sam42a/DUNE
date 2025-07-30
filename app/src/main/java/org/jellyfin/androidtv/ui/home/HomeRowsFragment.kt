package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.home.HomeFragmentViewsRow
import org.jellyfin.androidtv.preference.UserPreferences

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.GenreRowPreferences
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import androidx.leanback.widget.RowHeaderPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val genreRowPreferences by inject<GenreRowPreferences>()

	private val userPreferences by inject<UserPreferences>()
	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userPreferences) }

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true
	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository, navigationRepository) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Create a custom row presenter that keeps headers always visible
		val rowPresenter = PositionableListRowPresenter(requireContext()).apply {
			// Enable select effect for rows
			setSelectEffectEnabled(true)
		}

		// Set the adapter with our custom row presenter
		adapter = MutableObjectAdapter<Row>(rowPresenter)

		lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// Start out with default sections
			val homesections = listOf(
    userSettingPreferences.get(userSettingPreferences.homesection0),
    userSettingPreferences.get(userSettingPreferences.homesection1),
    userSettingPreferences.get(userSettingPreferences.homesection2),
    userSettingPreferences.get(userSettingPreferences.homesection3),
    userSettingPreferences.get(userSettingPreferences.homesection4),
    userSettingPreferences.get(userSettingPreferences.homesection5),
    userSettingPreferences.get(userSettingPreferences.homesection6),
    userSettingPreferences.get(userSettingPreferences.homesection7),
    userSettingPreferences.get(userSettingPreferences.homesection8),
    userSettingPreferences.get(userSettingPreferences.homesection9)
)
			var includeLiveTvRows = false

			// Check for live TV support
			if (homesections.contains(HomeSectionType.LIVE_TV) && currentUser.policy?.enableLiveTvAccess == true) {
				// This is kind of ugly, but it mirrors how web handles the live TV rows on the home screen
				// If we can retrieve one live TV recommendation, then we should display the rows
				val recommendedPrograms by api.liveTvApi.getRecommendedPrograms(
					enableTotalRecordCount = false,
					imageTypeLimit = 1,
					isAiring = true,
					limit = 1,
				)
				includeLiveTvRows = recommendedPrograms.items.isNotEmpty()
			}

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			for (section in homesections) when (section) {
				HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(userViewsRepository.views.first()))
				HomeSectionType.LIBRARY_TILES_SMALL -> rows.add(HomeFragmentViewsRow(small = false))
				HomeSectionType.LIBRARY_BUTTONS -> rows.add(HomeFragmentViewsRow(small = true))
				HomeSectionType.RESUME -> rows.add(helper.loadResumeVideo())
				HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
				HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
				HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
				HomeSectionType.NEXT_UP -> rows.add(helper.loadNextUp())
				HomeSectionType.LIVE_TV -> if (includeLiveTvRows) {
					rows.add(liveTVRow)
					rows.add(helper.loadOnNow())
				}

				HomeSectionType.NONE -> Unit
			}

			// Add sections to layout
			withContext(Dispatchers.Main) {
				// Create the CardPresenter with the desired size
				val cardPresenter = CardPresenter(true, 195).apply {
                    // Set home screen flag to adjust episode card sizes
                    setHomeScreen(true)
                } // 150px * 1.3 = 195px height (30% increase)

				val rowsAdapter = adapter as? MutableObjectAdapter<Row> ?: return@withContext

				// Add notification and now playing rows first
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)

				// Add main content rows
				rows.forEach { row ->
					try {
						row.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
					} catch (e: Exception) {
						Timber.e(e, "Error adding row to adapter")
					}
				}

				// Additional rows can be added here if needed in the future

                // Add Music Videos row if enabled
                if (userSettingPreferences.get(userSettingPreferences.showMusicVideosRow)) {
                    try {
                        Timber.d("Adding Music Videos row")
                        helper.loadMusicVideosRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
                    } catch (e: Exception) {
                        Timber.e(e, "Error adding Music Videos row")
                    }
                }
				if (userSettingPreferences.get(userSettingPreferences.showSciFiRow)) {
                    helper.loadSciFiRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
                }
                if (userSettingPreferences.get(userSettingPreferences.showComedyRow)) {
                    helper.loadComedyRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
                }
				if (userSettingPreferences.get(userSettingPreferences.showRomanceRow)) {
					helper.loadRomanceRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showAnimeRow)) {
					helper.loadAnimeRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showActionRow)) {
					helper.loadActionRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showActionAdventureRow)) {
					helper.loadActionAdventureRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				}

				if (userSettingPreferences.get(userSettingPreferences.showDocumentaryRow)) {
					helper.loadDocumentaryRow().addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showRealityRow)) {
					val realityRow = helper.loadRealityRow()
					if (realityRow != null) {
						realityRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
					} else {
						Timber.d("No matching Reality genre found in library")
					}
				}
				val mutableAdapter = adapter as? MutableObjectAdapter<Row> ?: return@withContext

				if (userSettingPreferences.get(userSettingPreferences.showFamilyRow)) {
					Timber.d("Adding Family row: ${userSettingPreferences.get(userSettingPreferences.showFamilyRow)}")
					helper.loadFamilyRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showHorrorRow)) {
					Timber.d("Adding Horror row: ${userSettingPreferences.get(userSettingPreferences.showHorrorRow)}")
					helper.loadHorrorRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showFantasyRow)) {
					Timber.d("Adding Fantasy row: ${userSettingPreferences.get(userSettingPreferences.showFantasyRow)}")
					helper.loadFantasyRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showHistoryRow)) {
					Timber.d("Adding History row: ${userSettingPreferences.get(userSettingPreferences.showHistoryRow)}")
					helper.loadHistoryRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showMusicRow)) {
					Timber.d("Adding Music row: ${userSettingPreferences.get(userSettingPreferences.showMusicRow)}")
					helper.loadMusicRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showMysteryRow)) {
					Timber.d("Adding Mystery row: ${userSettingPreferences.get(userSettingPreferences.showMysteryRow)}")
					helper.loadMysteryRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showThrillerRow)) {
					Timber.d("Adding Thriller row: ${userSettingPreferences.get(userSettingPreferences.showThrillerRow)}")
					helper.loadThrillerRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}
				if (userSettingPreferences.get(userSettingPreferences.showWarRow)) {
					Timber.d("Adding War row: ${userSettingPreferences.get(userSettingPreferences.showWarRow)}")
					helper.loadWarRow().addToRowsAdapter(requireContext(), cardPresenter, mutableAdapter)
				}

			}
		}

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(liveTVRow::onItemClicked)
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Ensure views are updated when fragment is resumed
		ensureViewsInitialized()

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		(adapter as? MutableObjectAdapter<Row>)?.let { mutableAdapter ->
    nowPlaying.update(requireContext(), mutableAdapter)
}
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		(adapter as? MutableObjectAdapter<Row>)?.let { mutableAdapter ->
    nowPlaying.update(requireContext(), mutableAdapter)
}
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.Main) {
			if (delayed) delay(1.5.seconds)

			try {
				val size = adapter.size()
				repeat(size) { i ->
					val row = adapter[i] as? ListRow ?: return@repeat
					val rowAdapter = row.adapter as? ItemRowAdapter ?: return@repeat

					// Add a small delay between row refreshes to prevent UI freezing
					if (i > 0) delay(50)

					try {
						// Instead of clearing immediately, let's preserve the current items
						// and let the refresh update them in place
						if (force) {
							rowAdapter.Retrieve()
						} else {
							rowAdapter.ReRetrieveIfNeeded()
						}
					} catch (e: Exception) {
						Timber.e(e, "Error refreshing row at position $i")
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "Error during refreshRows")
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.d("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroyView() {
        // Clear references to views to prevent leaks
        titleView = null
        infoRowView = null
        summaryView = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaManager.removeAudioEventListener(this)
    }

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) return
			itemLauncher.launch(item, (row as ListRow).adapter as ItemRowAdapter, requireContext())
		}
	}

	private var titleView: android.widget.TextView? = null
    private var infoRowView: android.widget.LinearLayout? = null
    private var summaryView: android.widget.TextView? = null

    private fun ensureViewsInitialized() {
        // Always reinitialize the views when this is called
        activity?.let { activity ->
            titleView = activity.findViewById<android.widget.TextView>(R.id.title)
            infoRowView = activity.findViewById<android.widget.LinearLayout>(R.id.infoRow)
            summaryView = activity.findViewById<android.widget.TextView>(R.id.summary)

            // If we have a current item, update the views with its data
            currentItem?.let { item ->
                titleView?.setText(item.getName(requireContext()))
                summaryView?.setText(item.getSummary(requireContext()) ?: "")
                infoRowView?.removeAllViews()
                infoRowView?.let { view ->
                    org.jellyfin.androidtv.util.InfoLayoutHelper.addInfoRow(requireContext(), item.baseItem, view, true)
                }
            } ?: run {
                // Clear the views if there's no current item
                clearInfoPanel()
            }
        }
    }


    private fun clearInfoPanel(forceClear: Boolean = false) {
        titleView?.setText("")
        infoRowView?.removeAllViews()
        summaryView?.setText("")

        // Only clear backgrounds if we're not on a Media Folders item or if forced
        if (forceClear || currentItem == null || !homeFragmentViewsRow.isMediaFoldersItem(currentItem)) {
            backgroundService.clearBackgrounds()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ensureViewsInitialized()

        // Initialize any additional views here
        initializeViews()
    }

    private fun initializeViews() {
        // Any additional view initialization can go here
    }

private val homeFragmentViewsRow = HomeFragmentViewsRow(small = false)

private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
    override fun onItemSelected(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row?,
    ) {
        ensureViewsInitialized()

        // Check if the item is from the Media Folders row or has the media_folders_item tag
        val isMediaFolderItem = homeFragmentViewsRow.isMediaFoldersItem(item) ||
                              (item is BaseRowItem && itemViewHolder?.view?.tag == "media_folders_item")

        if (isMediaFolderItem) {
            // For Media Folders items, clear the info panel but keep the background
            titleView?.setText("")
            infoRowView?.removeAllViews()
            summaryView?.setText("")

            // Set the background using the Media Folder's primary image
            if (item is BaseRowItem) {
                currentItem = item
                currentRow = row as? ListRow
                backgroundService.setBackground(item.baseItem)
            }
            return
        }

        if (item !is BaseRowItem) {
            currentItem = null
            // Clear info panel and background
            clearInfoPanel(true)
        } else {
            currentItem = item
            currentRow = row as? ListRow

            // Safely cast row to ListRow and get its adapter
            (row as? ListRow)?.let { listRow ->
                val itemRowAdapter = listRow.adapter as? ItemRowAdapter
                itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))
            }

            // Fill info panel
            titleView?.setText(item.getName(requireContext()))
            summaryView?.setText(item.getSummary(requireContext()) ?: "")
            infoRowView?.removeAllViews()
            infoRowView?.let { view ->
                org.jellyfin.androidtv.util.InfoLayoutHelper.addInfoRow(requireContext(), item.baseItem, view, true)
            }

            backgroundService.setBackground(item.baseItem)
        }
    }
}
}
