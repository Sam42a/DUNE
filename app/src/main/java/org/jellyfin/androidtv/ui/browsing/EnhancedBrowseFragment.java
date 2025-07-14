package org.jellyfin.androidtv.ui.browsing;

import static org.koin.java.KoinJavaComponent.inject;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import timber.log.Timber;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.Extras;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.constant.LiveTvOption;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest;
import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.EnhancedDetailBrowseBinding;
import org.jellyfin.androidtv.ui.GridButton;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapterHelperKt;
import org.jellyfin.androidtv.ui.navigation.Destinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter;
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter;
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.InfoLayoutHelper;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.MarkdownRenderer;
import org.jellyfin.androidtv.util.sdk.compat.JavaCompat;
import org.jellyfin.sdk.api.client.ApiClient;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.koin.java.KoinJavaComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kotlin.Lazy;
import kotlinx.serialization.json.Json;

public class EnhancedBrowseFragment extends Fragment implements RowLoader, View.OnKeyListener {
    protected TextView mTitle;
    private LinearLayout mInfoRow;
    private TextView mSummary;

    protected static final int BY_LETTER = 0;
    protected static final int GENRES = 1;
    protected static final int RANDOM = 2;
    protected static final int SUGGESTED = 4;
    protected static final int GRID = 6;
    protected static final int ALBUMS = 7;
    protected static final int ARTISTS = 8;
    public static final int FAVSONGS = 9;
    protected static final int SCHEDULE = 10;
    protected static final int SERIES = 11;
    protected static final int ALBUM_ARTISTS = 12;
    protected BaseItemDto mFolder;
    protected BaseItemKind itemType;
    protected boolean showViews = true;
    protected boolean justLoaded = true;

    protected RowsSupportFragment mRowsFragment;
    protected CompositeClickedListener mClickedListener = new CompositeClickedListener();
    protected CompositeSelectedListener mSelectedListener = new CompositeSelectedListener();
    protected MutableObjectAdapter<Row> mRowsAdapter;
    protected ArrayList<BrowseRowDef> mRows = new ArrayList<>();
    protected CardPresenter mCardPresenter;
    protected BaseRowItem mCurrentItem;
    protected ListRow mCurrentRow;

    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private final Lazy<MarkdownRenderer> markdownRenderer = inject(MarkdownRenderer.class);
    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);
    private final Lazy<ApiClient> api = inject(ApiClient.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<KeyProcessor> keyProcessor = inject(KeyProcessor.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRowsAdapter = new MutableObjectAdapter<>(new PositionableListRowPresenter());

        setupViews();
        setupQueries(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        EnhancedDetailBrowseBinding binding = EnhancedDetailBrowseBinding.inflate(inflater, container, false);

        mTitle = binding.title;
        mInfoRow = binding.infoRow;
        mSummary = binding.summary;

        // Inject the RowsSupportFragment in the results container
        if (getChildFragmentManager().findFragmentById(R.id.rowsFragment) == null) {
            mRowsFragment = new RowsSupportFragment();
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.rowsFragment, mRowsFragment).commit();
        } else {
            mRowsFragment = (RowsSupportFragment) getChildFragmentManager()
                    .findFragmentById(R.id.rowsFragment);
        }

        assert mRowsFragment != null;
        mRowsFragment.setAdapter(mRowsAdapter);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set initial title from mFolder if available
        if (mTitle != null && mFolder != null && mFolder.getName() != null) {
            mTitle.setText(mFolder.getName());
        }

        setupEventListeners();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clear the backdrop when the fragment is paused
        clearBackdrop();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Clear the backdrop when the fragment is stopped
        clearBackdrop();
    }
    
    private void clearBackdrop() {
        try {
            if (backgroundService.getValue() != null) {
                backgroundService.getValue().clearBackgrounds();
            }
        } catch (Exception e) {
            Timber.e(e, "Error clearing backdrop");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mClickedListener.removeListeners();
        mSelectedListener.removeListeners();
        
        // Clear the backdrop when the view is destroyed
        if (backgroundService.getValue() != null) {
            backgroundService.getValue().clearBackgrounds();
        }
    }

    protected void setupQueries(RowLoader rowLoader) {
        rowLoader.loadRows(mRows);
    }

    protected void setupViews() {
        assert getArguments() != null;
        if (!getArguments().containsKey(Extras.Folder)) return;
        mFolder = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), getArguments().getString(Extras.Folder));
        if (mFolder == null) return;

        if (mFolder.getCollectionType() != null) {
            switch (mFolder.getCollectionType()) {
                case MOVIES:
                    itemType = BaseItemKind.MOVIE;
                    break;
                case TVSHOWS:
                    itemType = BaseItemKind.SERIES;
                    break;
                case MUSIC:
                    itemType = BaseItemKind.MUSIC_ALBUM;
                    break;
                default:
                    showViews = false;
            }
        } else {
            showViews = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // React to deletion
        DataRefreshService dataRefreshService = KoinJavaComponent.<DataRefreshService>get(DataRefreshService.class);
        if (mCurrentRow != null && mCurrentItem != null && mCurrentItem.getItemId() != null && mCurrentItem.getItemId().equals(dataRefreshService.getLastDeletedItemId())) {
            ((ItemRowAdapter) mCurrentRow.getAdapter()).remove(mCurrentItem);
            dataRefreshService.setLastDeletedItemId(null);
        }

        if (!justLoaded) {
            // Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
            if (mRowsAdapter != null) {
                refreshCurrentItem();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            return;

                        for (int i = 0; i < mRowsAdapter.size(); i++) {
                            if (mRowsAdapter.get(i) instanceof ListRow) {
                                if (((ListRow) Objects.requireNonNull(mRowsAdapter.get(i))).getAdapter() instanceof ItemRowAdapter) {
                                    ((ItemRowAdapter) ((ListRow) Objects.requireNonNull(mRowsAdapter.get(i))).getAdapter()).ReRetrieveIfNeeded();
                                }
                            }
                        }
                    }
                }, 1500);
            }
        } else {
            justLoaded = false;
        }
    }

    public void loadRows(List<BrowseRowDef> rows) {
        mRowsAdapter = new MutableObjectAdapter<>(new PositionableListRowPresenter());
        // Match home screen card configuration but hide info text
    mCardPresenter = new CardPresenter(false, 195); // Set showInfo to false to hide text below cards
    mCardPresenter.setHomeScreen(true);
        ClassPresenterSelector ps = new ClassPresenterSelector();
        ps.addClassPresenter(GridButtonBaseRowItem.class, new GridButtonPresenter(155, 140));
        ps.addClassPresenter(BaseRowItem.class, mCardPresenter);

        for (BrowseRowDef def : rows) {
            HeaderItem header = new HeaderItem(def.getHeaderText());
            ItemRowAdapter rowAdapter = switch (def.getQueryType()) {
                case NextUp -> new ItemRowAdapter(requireContext(), def.getNextUpQuery(), true, mCardPresenter, mRowsAdapter);
                case LatestItems -> new ItemRowAdapter(requireContext(), def.getLatestItemsQuery(), true, mCardPresenter, mRowsAdapter);
                case Views -> new ItemRowAdapter(requireContext(), GetUserViewsRequest.INSTANCE, mCardPresenter, mRowsAdapter);
                case SimilarSeries ->
                        new ItemRowAdapter(requireContext(), def.getSimilarQuery(), QueryType.SimilarSeries, mCardPresenter, mRowsAdapter);
                case SimilarMovies ->
                        new ItemRowAdapter(requireContext(), def.getSimilarQuery(), QueryType.SimilarMovies, mCardPresenter, mRowsAdapter);
                case LiveTvChannel -> new ItemRowAdapter(requireContext(), def.getTvChannelQuery(), 40, mCardPresenter, mRowsAdapter);
                case LiveTvProgram -> new ItemRowAdapter(requireContext(), def.getProgramQuery(), mCardPresenter, mRowsAdapter);
                case LiveTvRecording ->
                        new ItemRowAdapter(requireContext(), def.getRecordingQuery(), def.getChunkSize(), mCardPresenter, mRowsAdapter);
                case Premieres ->
                        new ItemRowAdapter(requireContext(), def.getQuery(), def.getChunkSize(), def.getPreferParentThumb(), def.isStaticHeight(), mCardPresenter, mRowsAdapter, def.getQueryType());
                case SeriesTimer -> new ItemRowAdapter(requireContext(), def.getSeriesTimerQuery(), mCardPresenter, mRowsAdapter);
                case Specials ->
                        new ItemRowAdapter(requireContext(), def.getSpecialsQuery(), new CardPresenter(false, 150), mRowsAdapter);
                default ->
                        new ItemRowAdapter(requireContext(), def.getQuery(), def.getChunkSize(), def.getPreferParentThumb(), def.isStaticHeight(), ps, mRowsAdapter, def.getQueryType());
            };

            rowAdapter.setReRetrieveTriggers(def.getChangeTriggers());

            ListRow row = new ListRow(header, rowAdapter);
            mRowsAdapter.add(row);
            rowAdapter.setRow(row);
            rowAdapter.Retrieve();
        }

        addAdditionalRows(mRowsAdapter);

        if (mRowsFragment != null) mRowsFragment.setAdapter(mRowsAdapter);
    }

    protected void addAdditionalRows(MutableObjectAdapter<Row> rowAdapter) {
        if (!showViews || itemType == null) return;

        HeaderItem gridHeader = new HeaderItem(rowAdapter.size(), getString(R.string.lbl_views));
        GridButtonPresenter mGridPresenter = new GridButtonPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);

        switch (itemType) {
            case MOVIE:
                gridRowAdapter.add(new GridButton(SUGGESTED, getString(R.string.lbl_suggested)));
                addStandardViewButtons(gridRowAdapter);
                gridRowAdapter.add(new GridButton(RANDOM, getString(R.string.random)));
                break;

            case MUSIC_ALBUM:
                gridRowAdapter.add(new GridButton(ALBUMS, getString(R.string.lbl_albums)));
                gridRowAdapter.add(new GridButton(ALBUM_ARTISTS, getString(R.string.lbl_album_artists)));
                gridRowAdapter.add(new GridButton(ARTISTS, getString(R.string.lbl_artists)));
                gridRowAdapter.add(new GridButton(GENRES, getString(R.string.lbl_genres)));
                gridRowAdapter.add(new GridButton(RANDOM, getString(R.string.random)));
                break;

            case SERIES:
                addStandardViewButtons(gridRowAdapter);
                gridRowAdapter.add(new GridButton(RANDOM, getString(R.string.random)));
                break;

            default:
                addStandardViewButtons(gridRowAdapter);
                break;
        }

        rowAdapter.add(new ListRow(gridHeader, gridRowAdapter));
    }

    protected void addStandardViewButtons(ArrayObjectAdapter gridRowAdapter) {
        gridRowAdapter.add(new GridButton(GRID, getString(R.string.lbl_all_items)));
        gridRowAdapter.add(new GridButton(BY_LETTER, getString(R.string.lbl_by_letter)));
        gridRowAdapter.add(new GridButton(GENRES, getString(R.string.lbl_genres)));
        // Disabled because the screen doesn't behave properly
        // gridRowAdapter.add(new GridButton(PERSONS, getString(R.string.lbl_performers)));
    }

    protected void setupEventListeners() {
        mRowsFragment.setOnItemViewClickedListener(mClickedListener);
        mClickedListener.registerListener(new ItemViewClickedListener());
        if (showViews) mClickedListener.registerListener(new SpecialViewClickedListener());

        mRowsFragment.setOnItemViewSelectedListener(mSelectedListener);
        mSelectedListener.registerListener(new ItemViewSelectedListener());

        CoroutineUtils.readCustomMessagesOnLifecycle(getLifecycle(), customMessageRepository.getValue(), message -> {
            if (message.equals(CustomMessage.RefreshCurrentItem.INSTANCE)) refreshCurrentItem();
            return null;
        });
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        return keyProcessor.getValue().handleKey(keyCode, mCurrentItem, requireActivity());
    }

    private void refreshCurrentItem() {
        if (mCurrentRow == null || mCurrentItem == null) return;
        ItemRowAdapterHelperKt.refreshItem((ItemRowAdapter) mCurrentRow.getAdapter(), api.getValue(), this, mCurrentItem);
    }

    private final class SpecialViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            // Check if fragment is still attached to activity
            if (!isAdded() || getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
                return;
            }

            try {
                if (item instanceof GridButtonBaseRowItem) item = ((GridButtonBaseRowItem) item).getGridButton();
                if (!(item instanceof GridButton button)) return;

                NavigationRepository navRepo = navigationRepository.getValue();
                if (navRepo == null) return;

                switch (button.getId()) {
                    case GRID:
                        if (mFolder != null) {
                            navRepo.navigate(Destinations.INSTANCE.libraryBrowser(mFolder));
                        }
                        break;

                    case ALBUMS:
                        if (mFolder != null) {
                            mFolder = JavaCompat.copyWithDisplayPreferencesId(mFolder, mFolder.getId() + "AL");
                            navRepo.navigate(Destinations.INSTANCE.libraryBrowser(mFolder, BaseItemKind.MUSIC_ALBUM.getSerialName()));
                        }
                        break;

                    case ALBUM_ARTISTS:
                        if (mFolder != null) {
                            mFolder = JavaCompat.copyWithDisplayPreferencesId(mFolder, mFolder.getId() + "AR");
                            navRepo.navigate(Destinations.INSTANCE.libraryBrowser(mFolder, "AlbumArtist"));
                        }
                        break;

                    case ARTISTS:
                        if (mFolder != null) {
                            mFolder = JavaCompat.copyWithDisplayPreferencesId(mFolder, mFolder.getId() + "AR");
                            navRepo.navigate(Destinations.INSTANCE.libraryBrowser(mFolder, "Artist"));
                        }
                        break;

                    case BY_LETTER:
                        if (mFolder != null && itemType != null) {
                            navRepo.navigate(Destinations.INSTANCE.libraryByLetter(mFolder, itemType.getSerialName()));
                        }
                        break;

                    case GENRES:
                        if (mFolder != null && itemType != null) {
                            navRepo.navigate(Destinations.INSTANCE.libraryByGenres(mFolder, itemType.getSerialName()));
                        }
                        break;

                    case RANDOM:
                        if (mFolder != null && itemType != null && api.getValue() != null) {
                            BrowsingUtils.getRandomItem(api.getValue(), getViewLifecycleOwner(), mFolder, itemType, randomItem -> {
                                if (randomItem != null) {
                                    if (randomItem.getType() == BaseItemKind.MUSIC_ALBUM) {
                                        navRepo.navigate(Destinations.INSTANCE.itemList(randomItem.getId()));
                                    } else {
                                        navRepo.navigate(Destinations.INSTANCE.itemDetails(randomItem.getId()));
                                    }
                                }
                                return null;
                            });
                        }
                        break;

                    case SUGGESTED:
                        if (mFolder != null) {
                            navRepo.navigate(Destinations.INSTANCE.librarySuggestions(mFolder));
                        }
                        break;

                    case FAVSONGS:
                        if (mFolder != null) {
                            navRepo.navigate(Destinations.INSTANCE.musicFavorites(mFolder.getId()));
                        }
                        break;

                    case SERIES:
                    case LiveTvOption.LIVE_TV_SERIES_OPTION_ID:
                        navRepo.navigate(Destinations.INSTANCE.getLiveTvSeriesRecordings());
                        break;

                    case SCHEDULE:
                    case LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID:
                        navRepo.navigate(Destinations.INSTANCE.getLiveTvSchedule());
                        break;

                    case LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID:
                        navRepo.navigate(Destinations.INSTANCE.getLiveTvRecordings());
                        break;

                    case LiveTvOption.LIVE_TV_GUIDE_OPTION_ID:
                        navRepo.navigate(Destinations.INSTANCE.getLiveTvGuide());
                        break;

                    default:
                        Context context = getContext();
                        if (context != null) {
                            Toast.makeText(context, item + getString(R.string.msg_not_implemented), Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            } catch (Exception e) {
                // Catch any potential exceptions to prevent crashes
                Timber.e(e, "Error in SpecialViewClickedListener");
            }
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            // Check if fragment is still attached to activity
            if (!isAdded() || getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
                return;
            }

            try {
                if (!(item instanceof BaseRowItem)) return;

                // Get the context safely
                Context context = getContext();
                if (context == null) return;

                // Get the adapter safely
                if (!(row instanceof ListRow)) return;
                ItemRowAdapter adapter = (ItemRowAdapter) ((ListRow) row).getAdapter();
                if (adapter == null) return;

                // Launch the item
                if (itemLauncher.getValue() != null) {
                    itemLauncher.getValue().launch((BaseRowItem) item, adapter, context);
                }
            } catch (Exception e) {
                // Catch any potential exceptions to prevent crashes
                Timber.e(e, "Error in onItemClicked");
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            // Check if fragment is still attached to activity
            if (!isAdded() || getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
                return;
            }

            try {
                if (!(item instanceof BaseRowItem rowItem)) {
                    if (mTitle != null) mTitle.setText(mFolder != null ? mFolder.getName() : "");
                    if (mInfoRow != null) mInfoRow.removeAllViews();
                    if (mSummary != null) mSummary.setText("");
                    mCurrentItem = null;
                    mCurrentRow = null;
                    // Clear the backdrop when no item is selected
                    if (backgroundService.getValue() != null) {
                        backgroundService.getValue().clearBackgrounds();
                    }
                    return;
                }


                mCurrentItem = (BaseRowItem) item;
                mCurrentRow = (ListRow) row;
                if (mInfoRow != null) mInfoRow.removeAllViews();

                if (mTitle != null) {
                    mTitle.setText(mCurrentItem.getName(requireContext()));
                }

                if (mSummary != null) {
                    String summary = mCurrentItem.getSummary(requireContext());
                    if (summary != null) {
                        mSummary.setText(markdownRenderer.getValue().toMarkdownSpanned(summary));
                    } else {
                        mSummary.setText(null);
                    }
                }

                if (mInfoRow != null && getContext() != null) {
                    InfoLayoutHelper.addInfoRow(requireContext(), mCurrentItem.getBaseItem(), mInfoRow, true, false);
                }

                if (row != null) {
                    ItemRowAdapter adapter = (ItemRowAdapter) ((ListRow) row).getAdapter();
                    if (adapter != null) {
                        adapter.loadMoreItemsIfNeeded(adapter.indexOf(mCurrentItem));
                    }
                }

                // Set the backdrop for the selected item
                if (backgroundService.getValue() != null) {
                    backgroundService.getValue().setBackground(mCurrentItem.getBaseItem());
                }
            } catch (Exception e) {
                // Catch any potential exceptions to prevent crashes
                Timber.e(e, "Error in onItemSelected");
            }
        }
    }
}
