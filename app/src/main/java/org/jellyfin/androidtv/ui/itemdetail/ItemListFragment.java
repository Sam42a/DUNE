package org.jellyfin.androidtv.ui.itemdetail;

import static org.koin.java.KoinJavaComponent.inject;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.FragmentItemListBinding;
import org.jellyfin.androidtv.databinding.ViewRowDetailsBinding;
import org.jellyfin.androidtv.ui.AsyncImageView;
import org.jellyfin.androidtv.ui.ItemListView;
import org.jellyfin.androidtv.ui.ItemListViewHelperKt;
import org.jellyfin.androidtv.ui.ItemRowView;
import org.jellyfin.androidtv.ui.TextUnderButton;
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.navigation.Destinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.playback.AudioEventListener;
import org.jellyfin.androidtv.ui.playback.MediaManager;
import org.jellyfin.androidtv.ui.playback.PlaybackController;
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.InfoLayoutHelper;
import org.jellyfin.androidtv.util.PlaybackHelper;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.sdk.BaseItemExtensionsKt;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.ItemSortBy;
import org.jellyfin.sdk.model.api.MediaType;
import org.jellyfin.sdk.model.api.SortOrder;
import org.koin.java.KoinJavaComponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import kotlin.Lazy;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import timber.log.Timber;

public class ItemListFragment extends Fragment implements View.OnKeyListener {
    private int BUTTON_SIZE;

    private TextView mTitle;
    private TextView mGenreRow;
    private AsyncImageView mPoster;
    private TextView mSummary;
    private LinearLayout mButtonRow;
    private ItemListView mItemList;
    private ScrollView mScrollView;
    private ItemRowView mCurrentRow;

    private ItemRowView mCurrentlyPlayingRow;

    private BaseItemDto mBaseItem;
    private List<BaseItemDto> mItems = new ArrayList<>();

    private int mBottomScrollThreshold;

    private DisplayMetrics mMetrics;

    private boolean firstTime = true;
    private Instant lastUpdated = Instant.now();

    private Map<Integer, SortOption> sortOptions;
    private ItemSortBy currentSortBy = ItemSortBy.SORT_NAME;
    private SortOrder currentSortOrder = SortOrder.ASCENDING;

    // Lazy loading fields, hopefully lol
    private int mCurrentLoadedCount = 0;
    private static final int BATCH_SIZE = 25;  // Reduced from 50 - smaller batches load faster
    private static final int INITIAL_BATCH_SIZE = 15;  // Even smaller first batch for immediate display
    private boolean mIsLoadingMore = false;
    private int mTotalItemCount = 0;
    private final Map<UUID, Double> imageAspectCache = new HashMap<>();  // Cache for image aspect ratios
    private BaseItemDto lastBackdropItem = null;  // Cache for backdrop item
    private long lastScrollLoadTime = 0;
    private static final long SCROLL_LOAD_DEBOUNCE = 300; // ms
    private ProgressBar loadingIndicator;
    private boolean isLoading = false;
    private int activeLoaders = 0;

    private final Lazy<DataRefreshService> dataRefreshService = inject(DataRefreshService.class);
    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private final Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<PlaybackHelper> playbackHelper = inject(PlaybackHelper.class);
    private final Lazy<ImageHelper> imageHelper = inject(ImageHelper.class);

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentItemListBinding binding = FragmentItemListBinding.inflate(getLayoutInflater(), container, false);

        BUTTON_SIZE = Utils.convertDpToPixel(requireContext(), 35);

        ViewRowDetailsBinding detailsBinding = binding.details.getBinding();
        mTitle = detailsBinding.fdTitle;
        mTitle.setText(getString(R.string.loading));
        mGenreRow = detailsBinding.fdGenreRow;
        mPoster = detailsBinding.mainImage;
        mButtonRow = detailsBinding.fdButtonRow;
        mSummary = detailsBinding.fdSummaryText;
        mItemList = binding.songs;
        mScrollView = binding.scrollView;
        loadingIndicator = binding.loadingIndicator;

        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        mBottomScrollThreshold = (int) (mMetrics.heightPixels * .6);

        //Item list listeners
        mItemList.setRowSelectedListener(new ItemRowView.RowSelectedListener() {
            @Override
            public void onRowSelected(ItemRowView row) {
                mCurrentRow = row;
                //Keep selected row in center of screen
                int[] location = new int[]{0, 0};
                row.getLocationOnScreen(location);
                int y = location[1];
                if (y > mBottomScrollThreshold) {
                    // too close to bottom - scroll down
                    mScrollView.smoothScrollBy(0, y - mBottomScrollThreshold);
                }
            }
        });

        mItemList.setRowClickedListener(new ItemRowView.RowClickedListener() {
            @Override
            public void onRowClicked(ItemRowView row) {
                showMenu(row, row.getItem().getType() != BaseItemKind.AUDIO);
            }
        });

        mScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (mScrollView.getChildCount() > 0) {
                View child = mScrollView.getChildAt(0);
                int scrollViewHeight = mScrollView.getHeight();
                int childHeight = child.getHeight();

                if (scrollY + scrollViewHeight >= childHeight - 100) {
                    onScrolledToBottom();
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UUID mItemId = Utils.uuidOrNull(getArguments().getString("ItemId"));
        ItemListFragmentHelperKt.loadItem(this, mItemId);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;

        if (mediaManager.getValue().isPlayingAudio()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    mediaManager.getValue().togglePlayPause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    mediaManager.getValue().nextAudioItem();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    mediaManager.getValue().prevAudioItem();
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    showMenu(mCurrentRow, false);
                    return true;
            }
        } else if (mCurrentRow != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MENU:
                    showMenu(mCurrentRow, false);
                    return true;
            }
        }

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        mediaManager.getValue().addAudioEventListener(mAudioEventListener);
        // and fire it to be sure we're updated
        mAudioEventListener.onPlaybackStateChange(mediaManager.getValue().isPlayingAudio() ? PlaybackController.PlaybackState.PLAYING : PlaybackController.PlaybackState.IDLE, mediaManager.getValue().getCurrentAudioItem());

        if (!firstTime && dataRefreshService.getValue().getLastPlayback() != null && dataRefreshService.getValue().getLastPlayback().isAfter(lastUpdated)) {
            if (MediaType.VIDEO.equals(mBaseItem.getMediaType())) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            return;

                        ItemListViewHelperKt.refresh(mItemList);
                        lastUpdated = Instant.now();

                    }
                }, 500);
            }
        }

        firstTime = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        mediaManager.getValue().removeAudioEventListener(mAudioEventListener);
        if (loadingIndicator != null && activeLoaders > 0) {
            activeLoaders = 0;
            loadingIndicator.animate().cancel();
            loadingIndicator.setVisibility(View.GONE);
            loadingIndicator.setAlpha(1f);
        }
    }

    private AudioEventListener mAudioEventListener = new AudioEventListener() {
        @Override
        public void onPlaybackStateChange(@NonNull PlaybackController.PlaybackState newState, @Nullable BaseItemDto currentItem) {
            Timber.i("Got playback state change event %s for item %s", newState.toString(), currentItem != null ? currentItem.getName() : "<unknown>");

            if (newState != PlaybackController.PlaybackState.PLAYING || currentItem == null) {
                if (mCurrentlyPlayingRow != null) mCurrentlyPlayingRow.updateCurrentTime(-1);
                mCurrentlyPlayingRow = mItemList.updatePlaying(null);
            } else {
                mCurrentlyPlayingRow = mItemList.updatePlaying(currentItem.getId());
            }
        }

        @Override
        public void onProgress(long pos) {
            if (mCurrentlyPlayingRow != null) {
                mCurrentlyPlayingRow.updateCurrentTime(pos);
            }
        }

        @Override
        public void onQueueStatusChanged(boolean hasQueue) {
        }

        @Override
        public void onQueueReplaced() {
        }
    };

    private void showMenu(final ItemRowView row, boolean showOpen) {
        PopupMenu menu = new PopupMenu(requireContext(), row != null ? row : requireActivity().getCurrentFocus(), Gravity.END);
        int order = 0;
        if (showOpen) {
            MenuItem open = menu.getMenu().add(0, 0, order++, R.string.lbl_open);
            open.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    itemLauncher.getValue().launch(new BaseItemDtoBaseRowItem(row.getItem()), null, requireContext());
                    return true;
                }
            });

        }
        MenuItem playFromHere = menu.getMenu().add(0, 0, order++, R.string.lbl_play_from_here);
        playFromHere.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                play(mItems, row.getIndex(), false);
                return true;
            }
        });
        MenuItem play = menu.getMenu().add(0, 1, order++, R.string.lbl_play);
        play.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                play(mItems.subList(row.getIndex(), row.getIndex() + 1), false);
                return true;
            }
        });
        if (row.getItem().getType() == BaseItemKind.AUDIO) {
            MenuItem queue = menu.getMenu().add(0, 2, order++, R.string.lbl_add_to_queue);
            queue.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mediaManager.getValue().queueAudioItem(row.getItem());
                    return true;
                }
            });

            MenuItem mix = menu.getMenu().add(0, 1, order++, R.string.lbl_instant_mix);
            mix.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    playbackHelper.getValue().playInstantMix(requireContext(), row.getItem());
                    return true;
                }
            });

        }

        menu.show();
    }

    public void setBaseItem(BaseItemDto item) {
        mBaseItem = item;

        LinearLayout mainInfoRow = requireActivity().findViewById(R.id.fdMainInfoRow);

        InfoLayoutHelper.addInfoRow(requireContext(), item, mainInfoRow, false, false);
        addGenres(mGenreRow);
        addButtons(BUTTON_SIZE);
        mSummary.setText(mBaseItem.getOverview());

        Double aspect = imageHelper.getValue().getImageAspectRatio(item, false);
        String primaryImageUrl = imageHelper.getValue().getPrimaryImageUrl(item, null, null);
        mPoster.setPadding(0, 0, 0, 0);
        mPoster.load(primaryImageUrl, null, ContextCompat.getDrawable(requireContext(), R.drawable.ic_album), aspect, 0);

        ItemListFragmentHelperKt.getPlaylist(this, mBaseItem, currentSortBy, currentSortOrder, itemResponse);
    }

    private void setLoading(boolean loading) {
        if (getActivity() == null || loadingIndicator == null) return;

        getActivity().runOnUiThread(() -> {
            // Cancel any ongoing animations
            loadingIndicator.animate().cancel();

            if (loading) {
                activeLoaders++;
                // Show and fade in the loading indicator
                if (loadingIndicator.getVisibility() != View.VISIBLE) {
                    loadingIndicator.setAlpha(0f);
                    loadingIndicator.setVisibility(View.VISIBLE);
                }
                loadingIndicator.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
            } else {
                activeLoaders = Math.max(0, activeLoaders - 1);
                if (activeLoaders == 0) {
                    loadingIndicator.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            if (getActivity() != null && loadingIndicator != null) {
                                loadingIndicator.clearAnimation();
                                loadingIndicator.setVisibility(View.GONE);
                                loadingIndicator.setAlpha(1f); // Reset alpha for next time
                                loadingIndicator.setIndeterminate(false);
                                loadingIndicator.setIndeterminate(true);
                            }
                        })
                        .start();
                }
            }
        });
    }

    // Force stop loading indicator
    private void forceStopLoading() {
        if (getActivity() == null || loadingIndicator == null) return;

        getActivity().runOnUiThread(() -> {
            activeLoaders = 0;
            loadingIndicator.animate().cancel();
            loadingIndicator.clearAnimation();
            loadingIndicator.setVisibility(View.GONE);
            loadingIndicator.setAlpha(1f);
        });
    }

    private Function1<List<BaseItemDto>, Unit> itemResponse = (List<BaseItemDto> items) -> {
        forceStopLoading();

        try {
            mTitle.setText(mBaseItem.getName());
            if (mTitle.getText().length() > 32) {
                mTitle.setTextSize(32);
            }

            if (!items.isEmpty()) {
                mCurrentLoadedCount = 0;
                mItems = new ArrayList<>(items.size() > 100 ? 100 : items.size()); // Pre-allocate with expected size
                mTotalItemCount = items.size();

                mItemList.clear();

                int batchSize = Math.min(INITIAL_BATCH_SIZE, items.size());
                for (int i = 0; i < batchSize; i++) {
                    BaseItemDto item = items.get(i);
                    mItemList.addItem(item, i);
                    mItems.add(item);
                }
                mCurrentLoadedCount = batchSize;

                if (items.size() > INITIAL_BATCH_SIZE) {
                    loadNextBatchBackground(items, batchSize);
                } else {
                    setLoading(false);
                }

                if (mediaManager.getValue().isPlayingAudio()) {
                    mAudioEventListener.onPlaybackStateChange(PlaybackController.PlaybackState.PLAYING, mediaManager.getValue().getCurrentAudioItem());
                }

                updateBackdrop();
            } else {
                setLoading(false);
            }
        } catch (Exception e) {
            setLoading(false);
        }
        return null;
    };

    private void loadNextBatchBackground(List<BaseItemDto> allItems, int startIndex) {
        boolean isFirstBatch = (startIndex == INITIAL_BATCH_SIZE);
        if (isFirstBatch) {
            setLoading(true);
        }

        new Handler().postDelayed(() -> {
            try {
                if (startIndex >= allItems.size()) {
                    setLoading(false);
                    return;
                }

                int endIndex = Math.min(startIndex + BATCH_SIZE, allItems.size());
                for (int i = startIndex; i < endIndex; i++) {
                    if (i < allItems.size()) {
                        mItemList.addItem(allItems.get(i), i);
                        mItems.add(allItems.get(i));
                    }
                }
                mCurrentLoadedCount = endIndex;

                if (endIndex < allItems.size()) {
                    loadNextBatchBackground(allItems, endIndex);
                } else {
                    setLoading(false);
                    new Handler().postDelayed(() -> {
                        if (activeLoaders == 0 && loadingIndicator != null && loadingIndicator.getVisibility() == View.VISIBLE) {
                            forceStopLoading();
                        }
                    }, 500);
                }
            } catch (Exception e) {
                setLoading(false);
            }
        }, 200); // 200ms delay to not block UI immediately
    }

    private void addGenres(TextView textView) {
        List<String> genres = mBaseItem.getGenres();
        if (genres != null) textView.setText(TextUtils.join(" / ", genres));
        else textView.setText(null);
    }

    private void play(List<BaseItemDto> items, boolean shuffle) {
        play(items, 0, shuffle);
    }

    private void play(List<BaseItemDto> items, int ndx, boolean shuffle) {
        Timber.d("play items: %d, ndx: %d, shuffle: %b", items.size(), ndx, shuffle);

        int pos = 0;
        BaseItemDto item = items.size() > 0 ? items.get(ndx) : null;
        if (item != null && item.getUserData() != null) {
            pos = Math.toIntExact(item.getUserData().getPlaybackPositionTicks() / 10000);
        }
        KoinJavaComponent.<PlaybackLauncher>get(PlaybackLauncher.class).launch(getContext(), items, pos, false, ndx, shuffle);
    }

    private void addButtons(int buttonSize) {
        initializeSortOptions();

        if (BaseItemExtensionsKt.canPlay(mBaseItem)) {
            TextUnderButton play = TextUnderButton.create(requireContext(), R.drawable.ic_play, buttonSize, 2,
                getString(mBaseItem.isFolder() ? R.string.lbl_play_all : R.string.lbl_play), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mItems.size() > 0) {
                        play(mItems, false);
                    } else {
                        Utils.showToast(requireContext(), R.string.msg_no_playable_items);
                    }
                }
            });
            play.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) mScrollView.smoothScrollTo(0, 0);
            });
            mButtonRow.addView(play);

            mButtonRow.post(() -> addSecondaryButtons(buttonSize, play));
        }
    }

    private void addSecondaryButtons(int buttonSize, TextUnderButton play) {
        boolean hidePlayButton = false;
        TextUnderButton queueButton = null;

        if (mBaseItem.getType() == BaseItemKind.MUSIC_ALBUM && mediaManager.getValue().hasAudioQueueItems()) {
            queueButton = TextUnderButton.create(requireContext(), R.drawable.ic_add, buttonSize, 2,
                getString(R.string.lbl_add_to_queue), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mediaManager.getValue().addToAudioQueue(mItems);
                }
            });
            hidePlayButton = true;
            mButtonRow.addView(queueButton);
            queueButton.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) mScrollView.smoothScrollTo(0, 0);
            });
        }

        if (hidePlayButton) {
            play.setVisibility(View.GONE);
            if (queueButton != null) queueButton.requestFocus();
        } else {
            play.requestFocus();
        }

        if (mBaseItem.isFolder()) {
            TextUnderButton shuffle = TextUnderButton.create(requireContext(), R.drawable.ic_shuffle, buttonSize, 2,
                getString(R.string.lbl_shuffle_all), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mItems.isEmpty()) {
                        playbackHelper.getValue().retrieveAndPlay(mBaseItem.getId(), true, requireContext());
                    } else {
                        Utils.showToast(requireContext(), R.string.msg_no_playable_items);
                    }
                }
            });
            mButtonRow.addView(shuffle);
            shuffle.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) mScrollView.smoothScrollTo(0, 0);
            });
        }

        if (mBaseItem.getType() == BaseItemKind.MUSIC_ALBUM) {
            TextUnderButton mix = TextUnderButton.create(requireContext(), R.drawable.ic_mix, buttonSize, 2,
                getString(R.string.lbl_instant_mix), new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    playbackHelper.getValue().playInstantMix(requireContext(), mBaseItem);
                }
            });
            mButtonRow.addView(mix);
            mix.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) mScrollView.smoothScrollTo(0, 0);
            });
        }

        TextUnderButton fav = TextUnderButton.create(requireContext(), R.drawable.ic_heart, buttonSize, 2,
            getString(R.string.lbl_favorite), new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                ItemListFragmentHelperKt.toggleFavorite(ItemListFragment.this, mBaseItem, (BaseItemDto updatedItem) -> {
                    mBaseItem = updatedItem;
                    v.setActivated(mBaseItem.getUserData().isFavorite());
                    dataRefreshService.getValue().setLastFavoriteUpdate(Instant.now());
                    return null;
                });
            }
        });
        fav.setActivated(mBaseItem.getUserData().isFavorite());
        mButtonRow.addView(fav);
        fav.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) mScrollView.smoothScrollTo(0, 0);
        });

        addSortButton(buttonSize);

        if (mBaseItem.getAlbumArtists() != null && !mBaseItem.getAlbumArtists().isEmpty()) {
            TextUnderButton artist = TextUnderButton.create(requireContext(), R.drawable.ic_user, buttonSize, 4,
                getString(R.string.lbl_open_artist), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(mBaseItem.getAlbumArtists().get(0).getId()));
                }
            });
            mButtonRow.addView(artist);
            artist.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) mScrollView.smoothScrollTo(0, 0);
            });
        }
    }

    private void updateBackdrop() {
        BaseItemDto item = mBaseItem;

        if ((item.getBackdropImageTags() == null || item.getBackdropImageTags().isEmpty()) &&
            mItems != null && !mItems.isEmpty()) {
            if (lastBackdropItem == null) {
                lastBackdropItem = mItems.get(new Random().nextInt(mItems.size()));
            }
            item = lastBackdropItem;
        }

        backgroundService.getValue().setBackground(item);
    }

    public class SortOption {
        public String name;
        public ItemSortBy value;
        public SortOrder order;

        public SortOption(String name, ItemSortBy value, SortOrder order) {
            this.name = name;
            this.value = value;
            this.order = order;
        }
    }

    private void initializeSortOptions() {
        if (sortOptions != null) return; // Already initialized

        sortOptions = new HashMap<>();
        // Main sort options
        sortOptions.put(0, new SortOption(getString(R.string.lbl_name), ItemSortBy.SORT_NAME, SortOrder.ASCENDING));
        sortOptions.put(1, new SortOption(getString(R.string.lbl_date_added), ItemSortBy.DATE_CREATED, SortOrder.DESCENDING));
        sortOptions.put(2, new SortOption(getString(R.string.lbl_premier_date), ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING));
        sortOptions.put(3, new SortOption(getString(R.string.lbl_critic_rating), ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING));
        sortOptions.put(4, new SortOption("Airtime", ItemSortBy.AIRED_EPISODE_ORDER, SortOrder.DESCENDING));

        // Add Sort Order category
        sortOptions.put(100, new SortOption("Sort Order", null, null));
        sortOptions.put(101, new SortOption("Ascending", null, SortOrder.ASCENDING));
        sortOptions.put(102, new SortOption("Descending", null, SortOrder.DESCENDING));

        loadSortPreferences();
    }

    private SortOption getSortOption(ItemSortBy value) {
        if (value == null) {
            return new SortOption(getString(R.string.lbl_bracket_unknown), ItemSortBy.SORT_NAME, SortOrder.ASCENDING);
        }

        for (SortOption sortOption : sortOptions.values()) {
            if (sortOption.value != null && sortOption.value.equals(value)) {
                return sortOption;
            }
        }

        return new SortOption(getString(R.string.lbl_bracket_unknown), ItemSortBy.SORT_NAME, SortOrder.ASCENDING);
    }

    private void addSortButton(int buttonSize) {
        TextUnderButton sort = TextUnderButton.create(requireContext(), R.drawable.ic_sort, buttonSize, 2, getString(R.string.lbl_sort_by), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortMenu();
            }
        });
        mButtonRow.addView(sort);
        sort.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) mScrollView.smoothScrollTo(0, 0);
        });
    }

    private void showSortMenu() {
        PopupMenu sortMenu = new PopupMenu(requireContext(), mButtonRow, Gravity.END);

        for (Map.Entry<Integer, SortOption> entry : sortOptions.entrySet()) {
            SortOption option = entry.getValue();
            if (option.value != null) {
                MenuItem menuItem = sortMenu.getMenu().add(0, entry.getKey(), entry.getKey(), option.name);
                menuItem.setChecked(option.value != null && option.value.equals(currentSortBy) &&
                        option.order != null && option.order.equals(currentSortOrder));
            } else if (option.order != null) {
                MenuItem menuItem = sortMenu.getMenu().add(1, entry.getKey(), entry.getKey(), option.name);
                menuItem.setChecked(option.order.equals(currentSortOrder));
            } else {
                sortMenu.getMenu().add(2, entry.getKey(), entry.getKey(), option.name);
            }
        }

        // Make sort options checkable
        sortMenu.getMenu().setGroupCheckable(0, true, true);
        sortMenu.getMenu().setGroupCheckable(1, true, true);
        sortMenu.getMenu().setGroupEnabled(2, false); // Disable category header

        sortMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                SortOption selectedOption = sortOptions.get(item.getItemId());
                if (selectedOption != null) {
                    boolean shouldRefresh = false;
                    if (selectedOption.value != null) {
                        if (!currentSortBy.equals(selectedOption.value)) {
                            currentSortBy = selectedOption.value;
                            shouldRefresh = true;
                        }
                        currentSortOrder = selectedOption.order;
                    } else if (selectedOption.order != null) {
                        if (!currentSortOrder.equals(selectedOption.order)) {
                            currentSortOrder = selectedOption.order;
                            shouldRefresh = true;
                        }
                    }
                    if (shouldRefresh) {
                        saveSortPreferences();
                        setLoading(true);
                        ItemListFragmentHelperKt.getPlaylist(ItemListFragment.this, mBaseItem, currentSortBy, currentSortOrder, itemResponse);
                    }
                    return true;
                }
                return false;
            }
        });
        sortMenu.show();
    }

    private void saveSortPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences("ItemListFragment_Prefs", android.content.Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sortBy", currentSortBy.name());
        editor.putString("sortOrder", currentSortOrder.name());
        editor.apply();
    }

    private void loadSortPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences("ItemListFragment_Prefs", android.content.Context.MODE_PRIVATE);
        String sortByStr = prefs.getString("sortBy", ItemSortBy.SORT_NAME.name());
        String sortOrderStr = prefs.getString("sortOrder", SortOrder.ASCENDING.name());

        try {
            currentSortBy = ItemSortBy.valueOf(sortByStr);
            currentSortOrder = SortOrder.valueOf(sortOrderStr);
        } catch (IllegalArgumentException e) {
            // If saved value is invalid, use defaults
            currentSortBy = ItemSortBy.SORT_NAME;
            currentSortOrder = SortOrder.ASCENDING;
        }
    }

    private void loadMoreItems() {
        if (mIsLoadingMore || mBaseItem == null) {
            return;
        }

        mIsLoadingMore = true;
        setLoading(true);

        ItemListFragmentHelperKt.getPlaylistBatch(
            this,
            mBaseItem,
            currentSortBy,
            currentSortOrder,
            mCurrentLoadedCount,
            BATCH_SIZE,
            (items) -> {
                if (!items.isEmpty()) {
                    int startIndex = mCurrentLoadedCount;
                    for (BaseItemDto item : items) {
                        mItemList.addItem(item, startIndex++);
                        mItems.add(item);
                    }
                    mCurrentLoadedCount += items.size();
                }
                mIsLoadingMore = false;
                setLoading(false);
                return null;
            }
        );
    }

    public void onScrolledToBottom() {
        long now = System.currentTimeMillis();
        if (now - lastScrollLoadTime < SCROLL_LOAD_DEBOUNCE) {
            return; // Debounce rapid scroll events
        }
        lastScrollLoadTime = now;
        loadMoreItems();
    }
}
