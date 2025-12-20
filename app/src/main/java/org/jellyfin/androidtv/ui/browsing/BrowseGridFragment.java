package org.jellyfin.androidtv.ui.browsing;

import static org.koin.java.KoinJavaComponent.inject;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.ChangeTriggerType;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.Extras;
import org.jellyfin.androidtv.constant.GridDirection;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.constant.PosterSize;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.FilterOptions;
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest;
import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.repository.UserViewsRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.HorizontalGridBrowseBinding;
import org.jellyfin.androidtv.databinding.PopupEmptyBinding;
import org.jellyfin.androidtv.preference.LibraryPreferences;
import org.jellyfin.androidtv.preference.PreferencesRepository;
import org.jellyfin.androidtv.ui.AlphaPickerView;
import org.jellyfin.androidtv.ui.VerticalAlphaPickerView;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapterHelperKt;
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.HorizontalGridPresenter;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.EmptyResponse;
import org.jellyfin.sdk.api.client.ApiClient;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.CollectionType;
import org.jellyfin.sdk.model.api.ItemSortBy;
import org.jellyfin.sdk.model.api.SortOrder;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import kotlin.Lazy;
import kotlinx.serialization.json.Json;
import timber.log.Timber;

class CustomVerticalGridPresenter extends VerticalGridPresenter {
    @Override
    protected void initializeGridViewHolder(ViewHolder vh) {
        super.initializeGridViewHolder(vh);
        try {
            java.lang.reflect.Field field = VerticalGridPresenter.ViewHolder.class.getDeclaredField("mItemBridgeAdapter");
            field.setAccessible(true);
            androidx.leanback.widget.ItemBridgeAdapter adapter = (androidx.leanback.widget.ItemBridgeAdapter) field.get(vh);
            if (adapter != null) {
                androidx.leanback.widget.FocusHighlightHelper.setupBrowseItemFocusHighlight(
                        adapter, androidx.leanback.widget.FocusHighlight.ZOOM_FACTOR_LARGE, false);
            }
        } catch (Exception e) {
        }
    }
}

class LeftAlignedVerticalGridPresenter extends CustomVerticalGridPresenter {
    @Override
    protected void initializeGridViewHolder(ViewHolder vh) {
        super.initializeGridViewHolder(vh);
        VerticalGridView gridView = vh.getGridView();
        ViewGroup.LayoutParams params = gridView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        gridView.setLayoutParams(params);
    }
}

public class BrowseGridFragment extends Fragment implements View.OnKeyListener {
    private static final int CHUNK_SIZE_MINIMUM = 60;
    private static final int MIN_NUM_CARDS = 5;
    private static final double CARD_SPACING_PCT = 4.0;
    private static final double CARD_SPACING_HORIZONTAL_BANNER_PCT = 0.5;

    private static class DimensionCache {
        final double cardWidth;
        final double cardHeight;
        final String key;
        DimensionCache(double width, double height, String key) {
            this.cardWidth = width;
            this.cardHeight = height;
            this.key = key;
        }
    }

    private static final Map<String, String> GENRE_NORMALIZATION_CACHE = new HashMap<>();
    static {
        GENRE_NORMALIZATION_CACHE.put("sci-fi", "science fiction");
        GENRE_NORMALIZATION_CACHE.put("science fiction", "science fiction");
        GENRE_NORMALIZATION_CACHE.put("sci fi", "science fiction");
        GENRE_NORMALIZATION_CACHE.put("rom-com", "romance");
        GENRE_NORMALIZATION_CACHE.put("rom com", "romance");
        GENRE_NORMALIZATION_CACHE.put("martial arts", "action");
        GENRE_NORMALIZATION_CACHE.put("superhero", "action");
        GENRE_NORMALIZATION_CACHE.put("animated", "animation");
        GENRE_NORMALIZATION_CACHE.put("doc", "documentary");
        GENRE_NORMALIZATION_CACHE.put("docu", "documentary");
    }

    private String mainTitle;
    private FragmentActivity mActivity;
    private BaseRowItem mCurrentItem;
    private CompositeClickedListener mClickedListener = new CompositeClickedListener();
    private CompositeSelectedListener mSelectedListener = new CompositeSelectedListener();
    private final Handler mHandler = new Handler();
    private int mCardHeight;
    private BrowseRowDef mRowDef;
    private CardPresenter mCardPresenter;
    private boolean justLoaded = true;
    private PosterSize mPosterSizeSetting = PosterSize.MED;
    private ImageType mImageType = ImageType.POSTER;
    private GridDirection mGridDirection = GridDirection.HORIZONTAL;
    private boolean determiningPosterSize = false;
    private UUID mParentId;
    private BaseItemDto mFolder;
    private LibraryPreferences libraryPreferences;
    private HorizontalGridBrowseBinding binding;
    private ItemRowAdapter mAdapter;
    private Presenter mGridPresenter;
    private Presenter.ViewHolder mGridViewHolder;
    private BaseGridView mGridView;
    private int mSelectedPosition = -1;
    private int mGridHeight = -1;
    private int mGridWidth = -1;
    private int mGridItemSpacingHorizontal = 0;
    private int mGridItemSpacingVertical = 0;
    private int mGridPaddingLeft = 0;
    private int mGridPaddingTop = 0;

    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private final Lazy<PreferencesRepository> preferencesRepository = inject(PreferencesRepository.class);
    private final Lazy<UserViewsRepository> userViewsRepository = inject(UserViewsRepository.class);
    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<KeyProcessor> keyProcessor = inject(KeyProcessor.class);
    private final Lazy<ApiClient> api = inject(ApiClient.class);

    private int mCardsScreenEst = 0;
    private int mCardsScreenStride = 0;
    private double mCardFocusScale = 1.15;
    private boolean mDirty = true;
    private DimensionCache mDimensionCache = null;
    private boolean mIsScrolling = false;

    private ImageButton mSortButton;
    private ImageButton mSettingsButton;
    private ImageButton mUnwatchedButton;
    private ImageButton mFavoriteButton;
    private ImageButton mLetterButton;
    private ImageButton mMasksButton;
    private VerticalAlphaPickerView mAlphabetSidebar;
    private Map<Integer, SortOption> sortOptions;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics display = requireContext().getResources().getDisplayMetrics();
        float density = display.density;
        float invDensity = 1.0f / density;
        mGridHeight = Math.round(display.heightPixels * invDensity - 130.6f);
        mGridWidth = Math.round(display.widthPixels * invDensity);
        mActivity = getActivity();
        mFolder = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), getArguments().getString(Extras.Folder));
        mParentId = mFolder.getId();
        mainTitle = mFolder.getName();
        String displayPreferencesId = mFolder.getDisplayPreferencesId() != null ? mFolder.getDisplayPreferencesId() : mFolder.getId().toString();
        libraryPreferences = preferencesRepository.getValue().getLibraryPreferences(displayPreferencesId);
        mPosterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        mImageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        mGridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());
        mCardFocusScale = getResources().getFraction(R.fraction.card_scale_focus, 1, 1);
        initializeGridPresenter();
        initializeSortOptions();
        setDefaultGridRowCols(mPosterSizeSetting, mImageType);
        setAutoCardGridValues();
        setupQueries();
        setupEventListeners();
    }

    private void initializeGridPresenter() {
        if (mGridDirection.equals(GridDirection.VERTICAL))
            setGridPresenter(new CustomVerticalGridPresenter());
        else if (mGridDirection.equals(GridDirection.LIST))
            setGridPresenter(new LeftAlignedVerticalGridPresenter());
        else
            setGridPresenter(new HorizontalGridPresenter());
    }

    private void initializeSortOptions() {
        sortOptions = new HashMap<>();
        sortOptions.put(0, new SortOption(getString(R.string.lbl_name), ItemSortBy.SORT_NAME, SortOrder.ASCENDING));
        sortOptions.put(1, new SortOption(getString(R.string.lbl_date_added), ItemSortBy.DATE_CREATED, SortOrder.DESCENDING));
        sortOptions.put(2, new SortOption(getString(R.string.lbl_premier_date), ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING));
        sortOptions.put(3, new SortOption(getString(R.string.lbl_rating), ItemSortBy.OFFICIAL_RATING, SortOrder.ASCENDING));
        sortOptions.put(4, new SortOption(getString(R.string.lbl_community_rating), ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING));
        sortOptions.put(5, new SortOption(getString(R.string.lbl_critic_rating), ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING));
        if (mFolder.getCollectionType() == CollectionType.TVSHOWS) {
            sortOptions.put(6, new SortOption(getString(R.string.lbl_last_played), ItemSortBy.SERIES_DATE_PLAYED, SortOrder.DESCENDING));
        } else {
            sortOptions.put(6, new SortOption(getString(R.string.lbl_last_played), ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING));
        }
        sortOptions.put(7, new SortOption(getString(R.string.lbl_random), ItemSortBy.RANDOM, SortOrder.ASCENDING));

        if (mFolder.getCollectionType() != null && mFolder.getCollectionType() == CollectionType.MOVIES) {
            sortOptions.put(8, new SortOption(getString(R.string.lbl_runtime), ItemSortBy.RUNTIME, SortOrder.ASCENDING));
        }
        sortOptions.put(9, new SortOption(getString(R.string.lbl_production_year), ItemSortBy.PRODUCTION_YEAR, SortOrder.DESCENDING));
        sortOptions.put(100, new SortOption("Sort Order", null, null));
        sortOptions.put(101, new SortOption("Ascending", null, SortOrder.ASCENDING));
        sortOptions.put(102, new SortOption("Descending", null, SortOrder.DESCENDING));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = HorizontalGridBrowseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        createGrid();
        view.post(() -> throttleScrollSpeed(view));
        loadGrid();
        addTools();
        setupAlphabetSidebar();
    }

    private void throttleScrollSpeed(View view) {
        VerticalGridView gridView = findVerticalGridView(view);

        if (gridView != null) {
            gridView.setOnFlingListener(new RecyclerView.OnFlingListener() {
                @Override
                public boolean onFling(int velocityX, int velocityY) {
                    int maxVelocity = 6000;
                    int cappedY = Math.max(Math.min(velocityY, maxVelocity), -maxVelocity);
                    int cappedX = Math.max(Math.min(velocityX, maxVelocity), -maxVelocity);

                    gridView.fling(cappedX, cappedY);
                    return true;
                }
            });
        }
    }

    private VerticalGridView findVerticalGridView(View view) {
        if (view instanceof VerticalGridView) {
            return (VerticalGridView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                VerticalGridView result = findVerticalGridView(viewGroup.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        return keyProcessor.getValue().handleKey(keyCode, mCurrentItem, mActivity);
    }

    @Override
    public void onPause() {
        super.onPause();
        backgroundService.getValue().clearBackgrounds();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        mGridView = null;
        mDimensionCache = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    private void createGrid() {
        if (mGridPresenter == null) return;
        mGridViewHolder = mGridPresenter.onCreateViewHolder(binding.rowsFragment);
        if (mGridViewHolder instanceof HorizontalGridPresenter.ViewHolder) {
            mGridView = ((HorizontalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            mGridView.setGravity(Gravity.CENTER_VERTICAL);
            ViewGroup.MarginLayoutParams clockMargin = (ViewGroup.MarginLayoutParams) binding.clock.getLayoutParams();
            mGridView.setPadding(0, mGridPaddingTop, clockMargin.getMarginEnd(), mGridPaddingTop);
        } else if (mGridViewHolder instanceof VerticalGridPresenter.ViewHolder) {
            mGridView = ((VerticalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            if (mGridDirection.equals(GridDirection.LIST)) {
                mGridView.setGravity(Gravity.START);
                mGridView.setPadding(0, mGridPaddingTop, mGridPaddingLeft, mGridPaddingTop);
            } else {
                mGridView.setGravity(Gravity.CENTER_HORIZONTAL);
                mGridView.setPadding(mGridPaddingLeft, mGridPaddingTop, mGridPaddingLeft, mGridPaddingTop);
            }
        }
        mGridView.setHorizontalSpacing(mGridItemSpacingHorizontal);
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);
        binding.rowsFragment.removeAllViews();
        binding.rowsFragment.addView(mGridViewHolder.view);
        setupScrollListener();
        updateAdapter();
    }

    private void setupScrollListener() {
        if (mGridView == null) return;
        mGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                boolean wasScrolling = mIsScrolling;
                mIsScrolling = (newState != RecyclerView.SCROLL_STATE_IDLE);
                if (wasScrolling != mIsScrolling && mAdapter != null) {
                    mAdapter.setScrollState(mIsScrolling);
                    if (!mIsScrolling && mAdapter.size() < mAdapter.getTotalItems()) {
                        mAdapter.loadMoreItemsIfNeeded(mAdapter.size() - 1);
                    }
                }
            }
        });
    }

    private void updateAdapter() {
        if (mGridView != null && mAdapter != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mSelectedPosition != -1 && mGridView.isAttachedToWindow()) {
                mGridView.setSelectedPosition(mSelectedPosition);
            }
        }
    }

    public void setGridPresenter(HorizontalGridPresenter gridPresenter) {
        if (gridPresenter == null) throw new IllegalArgumentException("Grid presenter may not be null");
        gridPresenter.setOnItemViewSelectedListener(mRowSelectedListener);
        gridPresenter.setOnItemViewClickedListener(mClickedListener);
        mGridPresenter = gridPresenter;
    }

    public void setGridPresenter(VerticalGridPresenter gridPresenter) {
        if (gridPresenter == null) throw new IllegalArgumentException("Grid presenter may not be null");
        gridPresenter.setOnItemViewSelectedListener(mRowSelectedListener);
        gridPresenter.setOnItemViewClickedListener(mClickedListener);
        mGridPresenter = gridPresenter;
    }

    public void setItem(BaseRowItem item) {
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

    public void setStatusText(String folderName) {
        if (folderName == null || binding == null || mAdapter == null) return;
        StringBuilder text = new StringBuilder(getString(R.string.lbl_showing)).append(" ");
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null || (!filters.isFavoriteOnly() && !filters.isUnwatchedOnly())) {
            text.append(getString(R.string.lbl_all_items));
        } else {
            if (filters.isUnwatchedOnly()) text.append(getString(R.string.lbl_unwatched)).append(" ");
            if (filters.isFavoriteOnly()) text.append(getString(R.string.lbl_favorites));
        }
        text.append(" ").append(getString(R.string.lbl_from)).append(" ").append(folderName);
        if (mAdapter.getSortBy() != null) {
            text.append(" ").append(getString(R.string.lbl_sorted_by)).append(" ").append(mAdapter.getSortBy().toString());
        }
        String genreFilter = mAdapter.getGenreFilter();
        if (genreFilter != null && !genreFilter.isEmpty()) {
            text.append(", Genre: ").append(genreFilter);
        }
        if (mAdapter.getStartLetter() != null) {
            text.append(" ").append(getString(R.string.lbl_starting_with)).append(" ").append(mAdapter.getStartLetter());
        }
        if (binding.statusText != null) {
            binding.statusText.setText(text.toString());
        }
    }

    private void updateItemInfoDisplay(BaseRowItem rowItem) {
        if (binding == null || binding.itemTitleContainer == null) return;

        StringBuilder titleText = new StringBuilder();
        boolean hasAdditionalInfo = false;
        BaseItemDto baseItem = rowItem.getBaseItem();
        String title = rowItem.getName(requireContext());
        if (title != null && !title.isEmpty()) {
            titleText.append(title);
        }

        if (baseItem != null) {
            if (baseItem.getRunTimeTicks() != null && baseItem.getRunTimeTicks() > 0) {
                hasAdditionalInfo = true;
                if (titleText.length() > 0) {
                    titleText.append("  •  ");
                }
                long minutes = baseItem.getRunTimeTicks() / 600000000L;
                if (minutes >= 60) {
                    long hours = minutes / 60;
                    long remainingMinutes = minutes % 60;
                    titleText.append(hours).append("h ").append(remainingMinutes).append("m");
                } else {
                    titleText.append(minutes).append("m");
                }
            }

            if (baseItem.getProductionYear() != null) {
                hasAdditionalInfo = true;
                if (titleText.length() > 0) {
                    titleText.append("  •  ");
                }
                titleText.append(baseItem.getProductionYear());
            }
        }

        if (binding.itemTitle != null) {
            binding.itemTitle.setText(titleText.toString());
        }

        boolean hasCommunityRating = false;
        boolean hasCriticRating = false;

        if (baseItem != null) {
            if (baseItem.getCommunityRating() != null && baseItem.getCommunityRating() > 0) {
                hasCommunityRating = true;
                if (binding.starIcon != null) {
                    binding.starIcon.setVisibility(View.VISIBLE);
                    binding.starIcon.setImageResource(R.drawable.ic_star);
                }
                if (binding.communityRating != null) {
                    binding.communityRating.setVisibility(View.VISIBLE);
                    binding.communityRating.setText(String.format("%.1f", baseItem.getCommunityRating()));
                }
            } else {
                if (binding.starIcon != null) binding.starIcon.setVisibility(View.GONE);
                if (binding.communityRating != null) binding.communityRating.setVisibility(View.GONE);
            }

            if (baseItem.getCriticRating() != null && baseItem.getCriticRating() > 0) {
                hasCriticRating = true;
                int tomatoDrawable = baseItem.getCriticRating() >= 60f ?
                        R.drawable.ic_rt_fresh : R.drawable.ic_rt_rotten;

                if (binding.tomatoIcon != null) {
                    binding.tomatoIcon.setVisibility(View.VISIBLE);
                    binding.tomatoIcon.setImageResource(tomatoDrawable);
                }
                if (binding.criticRating != null) {
                    binding.criticRating.setVisibility(View.VISIBLE);
                    binding.criticRating.setText(String.format("%.0f%%", baseItem.getCriticRating()));
                }
            } else {
                if (binding.tomatoIcon != null) binding.tomatoIcon.setVisibility(View.GONE);
                if (binding.criticRating != null) binding.criticRating.setVisibility(View.GONE);
            }
        }

        if (hasCommunityRating && hasCriticRating) {
            if (binding.ratingSeparator != null) {
                binding.ratingSeparator.setVisibility(View.VISIBLE);
            }
        } else {
            if (binding.ratingSeparator != null) {
                binding.ratingSeparator.setVisibility(View.GONE);
            }
        }

        if (hasAdditionalInfo && (hasCommunityRating || hasCriticRating)) {
            if (binding.yearRatingSeparator != null) {
                binding.yearRatingSeparator.setVisibility(View.VISIBLE);
            }
        } else {
            if (binding.yearRatingSeparator != null) {
                binding.yearRatingSeparator.setVisibility(View.GONE);
            }
        }

        if (titleText.length() > 0 || hasCommunityRating || hasCriticRating) {
            binding.itemTitleContainer.setVisibility(View.VISIBLE);
        } else {
            binding.itemTitleContainer.setVisibility(View.GONE);
        }
    }

    private final OnItemViewSelectedListener mRowSelectedListener = new OnItemViewSelectedListener() {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            int position = mGridView != null ? mGridView.getSelectedPosition() : -1;
            if (position != mSelectedPosition) {
                mSelectedPosition = position;
            }
            if (position >= 0) {
                updateCounter(position + 1);

                if (libraryPreferences != null && libraryPreferences.getShowItemTitlesOnFocus()) {
                    if (itemViewHolder != null && item instanceof BaseRowItem) {
                        BaseRowItem rowItem = (BaseRowItem) item;
                        updateItemInfoDisplay(rowItem);
                    }
                } else if (binding != null && binding.itemTitleContainer != null) {
                    binding.itemTitleContainer.setVisibility(View.GONE);
                }

                if (mSelectedListener != null) {
                    mSelectedListener.onItemSelected(itemViewHolder, item, rowViewHolder, row);
                }
            } else {
                if (binding != null && binding.itemTitleContainer != null) {
                    binding.itemTitleContainer.setVisibility(View.GONE);
                }
            }
        }
    };

    public void updateCounter(int position) {
        if (binding == null || mAdapter == null || position < 0) return;
        binding.counter.setText(MessageFormat.format("{0} | {1}", position, mAdapter.getTotalItems()));
    }

    private void setRowDef(final BrowseRowDef rowDef) {
        if (mRowDef == null || mRowDef.hashCode() != rowDef.hashCode()) {
            mDirty = true;
        }
        mRowDef = rowDef;
    }

    private double getCardWidthBy(final double cardHeight, ImageType imageType, BaseItemDto folder) {
        String cacheKey = cardHeight + "_" + imageType.name() + "_" + folder.getType().name();
        if (mDimensionCache != null && mDimensionCache.key.equals(cacheKey)) {
            return mDimensionCache.cardWidth;
        }
        double result;
        BaseItemKind fType = folder.getType();
        switch (imageType) {
            case POSTER:
                if (fType == BaseItemKind.AUDIO || fType == BaseItemKind.GENRE || fType == BaseItemKind.MUSIC_ALBUM || fType == BaseItemKind.MUSIC_ARTIST || fType == BaseItemKind.MUSIC_GENRE) {
                    result = cardHeight;
                } else if (fType == BaseItemKind.COLLECTION_FOLDER && CollectionType.MUSIC.equals(folder.getCollectionType())) {
                    result = cardHeight;
                } else {
                    result = cardHeight * ImageHelper.ASPECT_RATIO_2_3;
                }
                break;
            case THUMB:
                result = cardHeight * ImageHelper.ASPECT_RATIO_16_9;
                break;
            case BANNER:
                result = cardHeight * ImageHelper.ASPECT_RATIO_BANNER;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + imageType);
        }
        mDimensionCache = new DimensionCache(result, cardHeight, cacheKey);
        return result;
    }

    private double getCardHeightBy(final double cardWidth, ImageType imageType, BaseItemDto folder) {
        String cacheKey = cardWidth + "_" + imageType.name() + "_" + folder.getType().name();
        if (mDimensionCache != null && mDimensionCache.key.equals(cacheKey)) {
            return mDimensionCache.cardHeight;
        }
        double result;
        BaseItemKind fType = folder.getType();
        switch (imageType) {
            case POSTER:
                if (fType == BaseItemKind.AUDIO || fType == BaseItemKind.GENRE || fType == BaseItemKind.MUSIC_ALBUM || fType == BaseItemKind.MUSIC_ARTIST || fType == BaseItemKind.MUSIC_GENRE) {
                    result = cardWidth;
                } else if (fType == BaseItemKind.COLLECTION_FOLDER && CollectionType.MUSIC.equals(folder.getCollectionType())) {
                    result = cardWidth;
                } else {
                    result = cardWidth / ImageHelper.ASPECT_RATIO_2_3;
                }
                break;
            case THUMB:
                result = cardWidth / ImageHelper.ASPECT_RATIO_16_9;
                break;
            case BANNER:
                result = cardWidth / ImageHelper.ASPECT_RATIO_BANNER;
                break;
            default:
                throw new IllegalArgumentException("Unexpected value: " + imageType);
        }
        mDimensionCache = new DimensionCache(cardWidth, result, cacheKey);
        return result;
    }

    private void setDefaultGridRowCols(PosterSize posterSize, ImageType imageType) {
        if (mGridPresenter instanceof VerticalGridPresenter) {
            int numCols;
            if (mGridDirection.equals(GridDirection.LIST)) {
                numCols = 1;
            } else {
                switch (posterSize) {
                    case SMALLEST:
                        numCols = imageType.equals(ImageType.BANNER) ? 6 : imageType.equals(ImageType.THUMB) ? 11 : 15;
                        break;
                    case SMALL:
                        numCols = imageType.equals(ImageType.BANNER) ? 5 : imageType.equals(ImageType.THUMB) ? 9 : 13;
                        break;
                    case MED:
                        numCols = imageType.equals(ImageType.BANNER) ? 4 : imageType.equals(ImageType.THUMB) ? 7 : 11;
                        break;
                    case LARGE:
                        numCols = imageType.equals(ImageType.BANNER) ? 3 : imageType.equals(ImageType.THUMB) ? 5 : 7;
                        break;
                    case X_LARGE:
                        numCols = imageType.equals(ImageType.BANNER) ? 2 : imageType.equals(ImageType.THUMB) ? 3 : 5;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + mPosterSizeSetting);
                }
            }
            ((VerticalGridPresenter) mGridPresenter).setNumberOfColumns(numCols);
        } else if (mGridPresenter instanceof HorizontalGridPresenter) {
            int numRows;
            switch (posterSize) {
                case SMALLEST:
                    numRows = imageType.equals(ImageType.BANNER) ? 13 : imageType.equals(ImageType.THUMB) ? 7 : 5;
                    break;
                case SMALL:
                    numRows = imageType.equals(ImageType.BANNER) ? 11 : imageType.equals(ImageType.THUMB) ? 6 : 4;
                    break;
                case MED:
                    numRows = imageType.equals(ImageType.BANNER) ? 9 : imageType.equals(ImageType.THUMB) ? 5 : 3;
                    break;
                case LARGE:
                    numRows = imageType.equals(ImageType.BANNER) ? 7 : imageType.equals(ImageType.THUMB) ? 4 : 2;
                    break;
                case X_LARGE:
                    numRows = imageType.equals(ImageType.BANNER) ? 5 : imageType.equals(ImageType.THUMB) ? 2 : 1;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + mPosterSizeSetting);
            }
            ((HorizontalGridPresenter) mGridPresenter).setNumberOfRows(numRows);
        }
    }

    private void setAutoCardGridValues() {
        if (mGridPresenter == null) return;
        double cardScaling = Math.max(mCardFocusScale - 1.0, 0.0);
        int cardHeightInt = 100, cardWidthInt = 100, spacingHorizontalInt = 0, spacingVerticalInt = 0, paddingLeftInt = 0, paddingTopInt = 0, numRows = 0, numCols = 0;
        if (mGridPresenter instanceof HorizontalGridPresenter) {
            numRows = ((HorizontalGridPresenter) mGridPresenter).getNumberOfRows();
            if (numRows == 1) { numRows = 0; numCols = MIN_NUM_CARDS; }
        } else if (mGridPresenter instanceof VerticalGridPresenter) {
            numCols = ((VerticalGridPresenter) mGridPresenter).getNumberOfColumns();
        }
        if (numRows > 0) {
            double paddingPct = cardScaling / numRows;
            double spacingPct = ((paddingPct / 2.0) * CARD_SPACING_PCT) * (numRows - 1);
            double wastedSpacePct = paddingPct + spacingPct;
            double usableCardSpace = mGridHeight / (1.0 + wastedSpacePct);
            double cardHeight = usableCardSpace / numRows;
            cardHeightInt = (int) Math.round(cardHeight);
            double cardPaddingTopBottomAdj = cardHeightInt * cardScaling;
            spacingVerticalInt = Math.max((int) (Math.round((cardPaddingTopBottomAdj / 2.0) * CARD_SPACING_PCT)), 0);
            int paddingTopBottomInt = mGridHeight - ((cardHeightInt * numRows) + (spacingVerticalInt * (numRows - 1)));
            paddingTopInt = Math.max(paddingTopBottomInt / 2, 0);
            cardWidthInt = (int) getCardWidthBy(cardHeightInt, mImageType, mFolder);
            paddingLeftInt = (int) Math.round((cardWidthInt * cardScaling) / 2.0);
            spacingHorizontalInt = Math.max((int) (Math.round(paddingLeftInt * CARD_SPACING_PCT)), 0);
            if (mImageType == ImageType.BANNER) {
                spacingHorizontalInt = Math.max((int) (Math.round(paddingLeftInt * CARD_SPACING_HORIZONTAL_BANNER_PCT)), 0);
            }
            int cardsCol = (int) Math.round(((double) mGridWidth / (cardWidthInt + spacingHorizontalInt)) + 0.5);
            mCardsScreenEst = numRows * cardsCol;
            mCardsScreenStride = numRows;
        } else if (numCols > 0) {
            boolean isListLayout = mGridDirection.equals(GridDirection.LIST);
            double paddingPct = cardScaling / numCols;
            double spacingPct = ((paddingPct / 2.0) * CARD_SPACING_PCT) * (numCols - 1);
            if (mImageType == ImageType.BANNER) {
                spacingPct = ((paddingPct / 2.0) * CARD_SPACING_HORIZONTAL_BANNER_PCT) * (numCols - 1);
            }
            double wastedSpacePct = paddingPct + spacingPct;
            double usableCardSpace = mGridWidth / (1.0 + wastedSpacePct);
            double cardWidth = usableCardSpace / numCols;
            if (isListLayout) {
                switch (mImageType) {
                    case POSTER: cardWidthInt = 190; cardHeightInt = (int) Math.round(getCardHeightBy(cardWidthInt, mImageType, mFolder)); break;
                    case BANNER: cardWidthInt = 90; cardHeightInt = 94; break;
                    case THUMB: cardWidthInt = 328; cardHeightInt = 222; break;
                    default: cardWidthInt = (int) Math.round(cardWidth); cardHeightInt = (int) Math.round(getCardHeightBy(cardWidthInt, mImageType, mFolder)); break;
                }
            } else {
                cardHeightInt = (int) Math.round(getCardHeightBy(cardWidth, mImageType, mFolder));
                cardWidthInt = (int) getCardWidthBy(cardHeightInt, mImageType, mFolder);
            }
            double cardPaddingLeftRightAdj = cardWidthInt * cardScaling;
            spacingHorizontalInt = Math.max((int) (Math.round((cardPaddingLeftRightAdj / 2.0) * CARD_SPACING_PCT)), 0);
            if (mImageType == ImageType.BANNER) {
                spacingHorizontalInt = Math.max((int) (Math.round((cardPaddingLeftRightAdj / 2.0) * CARD_SPACING_HORIZONTAL_BANNER_PCT)), 0);
            }
            int paddingLeftRightInt = mGridWidth - ((cardWidthInt * numCols) + (spacingHorizontalInt * (numCols - 1)));
            paddingLeftInt = Math.max(paddingLeftRightInt / 2, 0);
            paddingTopInt = (int) Math.round((cardHeightInt * cardScaling) / 2.0);
            spacingVerticalInt = Math.max((int) (Math.round(paddingTopInt * CARD_SPACING_PCT)), 0);
            int cardsRow = (int) Math.round(((double) mGridHeight / (cardHeightInt + spacingVerticalInt)) + 0.5);
            mCardsScreenEst = numCols * cardsRow;
            mCardsScreenStride = numCols;
        }
        if (mCardHeight != cardHeightInt) mDirty = true;
        mCardHeight = cardHeightInt;
        mGridItemSpacingHorizontal = spacingHorizontalInt;
        mGridItemSpacingVertical = spacingVerticalInt;
        mGridPaddingLeft = paddingLeftInt;
        mGridPaddingTop = paddingTopInt;
    }

    private void setupQueries() {
        if (mFolder.getType() == BaseItemKind.USER_VIEW || mFolder.getType() == BaseItemKind.COLLECTION_FOLDER) {
            CollectionType type = mFolder.getCollectionType() != null ? mFolder.getCollectionType() : CollectionType.UNKNOWN;
            if (type == CollectionType.MUSIC) {
                String includeType = getArguments().getString(Extras.IncludeType, null);
                if ("AlbumArtist".equals(includeType)) {
                    setRowDef(new BrowseRowDef("", BrowsingUtils.createAlbumArtistsRequest(mParentId), CHUNK_SIZE_MINIMUM, new ChangeTriggerType[]{}));
                    return;
                } else if ("Artist".equals(includeType)) {
                    setRowDef(new BrowseRowDef("", BrowsingUtils.createArtistsRequest(mParentId), CHUNK_SIZE_MINIMUM, new ChangeTriggerType[]{}));
                    return;
                }
            }
        }
        setRowDef(new BrowseRowDef("", BrowsingUtils.createBrowseGridItemsRequest(mFolder), CHUNK_SIZE_MINIMUM, false, true));
    }

    @Override
    public void onResume() {
        super.onResume();
        PosterSize posterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        ImageType imageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        GridDirection gridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());
        if (mImageType != imageType || mPosterSizeSetting != posterSizeSetting || mGridDirection != gridDirection || mDirty) {
            determiningPosterSize = true;
            mImageType = imageType;
            mPosterSizeSetting = posterSizeSetting;
            mGridDirection = gridDirection;
            if (mGridDirection.equals(GridDirection.VERTICAL) && (mGridPresenter == null || !(mGridPresenter instanceof VerticalGridPresenter))) {
                setGridPresenter(new CustomVerticalGridPresenter());
            } else if (mGridDirection.equals(GridDirection.HORIZONTAL) && (mGridPresenter == null || !(mGridPresenter instanceof HorizontalGridPresenter))) {
                setGridPresenter(new HorizontalGridPresenter());
            } else if (mGridDirection.equals(GridDirection.LIST) && (mGridPresenter == null || !(mGridPresenter instanceof VerticalGridPresenter))) {
                setGridPresenter(new LeftAlignedVerticalGridPresenter());
            }
            setDefaultGridRowCols(mPosterSizeSetting, mImageType);
            setAutoCardGridValues();
            createGrid();
            loadGrid();
            determiningPosterSize = false;
        }
        if (!justLoaded && mAdapter != null) {
            mHandler.postDelayed(() -> {
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                if (mAdapter != null && mAdapter.size() > 0) {
                    if (!mAdapter.ReRetrieveIfNeeded()) refreshCurrentItem();
                }
            }, 500);
        } else {
            justLoaded = false;
        }
    }

    private void buildAdapter() {
        mCardPresenter = new CardPresenter(true, mImageType, mCardHeight, mGridDirection.equals(GridDirection.LIST));
        mCardPresenter.setUniformAspect(true);
        int chunkSize = mRowDef.getChunkSize();
        if (mCardsScreenEst > 0 && mCardsScreenEst >= chunkSize) {
            chunkSize = Math.min(mCardsScreenEst + mCardsScreenStride, 150);
        }
        switch (mRowDef.getQueryType()) {
            case NextUp: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getNextUpQuery(), true, mCardPresenter, null); break;
            case Views: mAdapter = new ItemRowAdapter(requireContext(), GetUserViewsRequest.INSTANCE, mCardPresenter, null); break;
            case SimilarSeries: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getSimilarQuery(), QueryType.SimilarSeries, mCardPresenter, null); break;
            case SimilarMovies: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getSimilarQuery(), QueryType.SimilarMovies, mCardPresenter, null); break;
            case LiveTvChannel: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getTvChannelQuery(), 40, mCardPresenter, null); break;
            case LiveTvProgram: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getProgramQuery(), mCardPresenter, null); break;
            case LiveTvRecording: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getRecordingQuery(), chunkSize, mCardPresenter, null); break;
            case Artists: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getArtistsQuery(), chunkSize, mCardPresenter, null); break;
            case AlbumArtists: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getAlbumArtistsQuery(), chunkSize, mCardPresenter, null); break;
            default: mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getQuery(), chunkSize, mRowDef.getPreferParentThumb(), mRowDef.isStaticHeight(), mCardPresenter, null); break;
        }
        mDirty = false;
        FilterOptions filters = new FilterOptions();
        filters.setFavoriteOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterFavoritesOnly()));
        filters.setUnwatchedOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterUnwatchedOnly()));
        mAdapter.setRetrieveFinishedListener(new EmptyResponse() {
            @Override
            public void onResponse() {
                setStatusText(mFolder.getName());
                if (mCurrentItem == null) {
                    setItem(null);
                    updateCounter(mAdapter.getTotalItems() > 0 ? 1 : 0);
                }
                mLetterButton.setVisibility(View.VISIBLE);
                if (mAdapter.getItemsLoaded() > 0 && mGridView != null && mGridView.isAttachedToWindow()) {
                    mGridView.setFocusable(true);
                    mGridView.requestFocus();
                }
            }
        });
        mAdapter.setFilters(filters);
        updateAdapter();
    }

    public void loadGrid() {
        if (mCardPresenter == null || mAdapter == null || mDirty) buildAdapter();
        mAdapter.setSortBy(getSortOption(libraryPreferences.get(LibraryPreferences.Companion.getSortBy())));
        mAdapter.Retrieve();
    }

    private void updateDisplayPrefs() {
        CoroutineUtils.runOnLifecycle(getLifecycle(), (coroutineScope, continuation) -> {
            libraryPreferences.set(LibraryPreferences.Companion.getFilterFavoritesOnly(), mAdapter.getFilters().isFavoriteOnly());
            libraryPreferences.set(LibraryPreferences.Companion.getFilterUnwatchedOnly(), mAdapter.getFilters().isUnwatchedOnly());
            libraryPreferences.set(LibraryPreferences.Companion.getSortBy(), mAdapter.getSortBy());
            libraryPreferences.set(LibraryPreferences.Companion.getSortOrder(), getSortOption(mAdapter.getSortBy()).order);
            return libraryPreferences.commit(continuation);
        });
    }

    private void addTools() {
        int size = Utils.convertDpToPixel(requireContext(), 27);
        mSortButton = createToolbarButton(R.drawable.ic_sort, getString(R.string.lbl_sort_by), "Filter", size, v -> showSortMenu());
        mMasksButton = createToolbarButton(R.drawable.ic_masks, getString(R.string.lbl_genres), "Genres", size, v -> showGenreMenu());
        mUnwatchedButton = createToolbarButton(R.drawable.ic_unwatch, getString(R.string.lbl_unwatched), "Watched", size, v -> toggleUnwatchedFilter());
        mFavoriteButton = createToolbarButton(R.drawable.ic_heart, getString(R.string.lbl_favorite), "Favorites", size, v -> toggleFavoriteFilter());
        mLetterButton = createToolbarButton(R.drawable.ic_jump_letter, getString(R.string.lbl_by_letter), "By letter", size, v -> new JumplistPopup().show());
        mSettingsButton = createToolbarButton(R.drawable.ic_settings, getString(R.string.lbl_settings), "Settings", size, v -> openSettings());
        mUnwatchedButton.setActivated(mAdapter.getFilters().isUnwatchedOnly());
        mFavoriteButton.setActivated(mAdapter.getFilters().isFavoriteOnly());
    }

    private ImageButton createToolbarButton(int iconRes, String contentDesc, String label, int size, View.OnClickListener clickListener) {
        LinearLayout buttonContainer = new LinearLayout(requireContext());
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER_VERTICAL);
        buttonContainer.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        containerParams.setMargins(0, 0, margin, 0);
        buttonContainer.setLayoutParams(containerParams);
        ImageButton button = new ImageButton(requireContext(), null, 0, R.style.Button_Icon);
        button.setImageResource(iconRes);
        button.setMaxHeight(size - 4);
        button.setAdjustViewBounds(true);
        button.setContentDescription(contentDesc);
        button.setOnClickListener(clickListener);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(size - 4, size - 4);
        button.setLayoutParams(buttonParams);
        TextView textView = new TextView(requireContext());
        textView.setText(label);
        textView.setTextSize(12);
        textView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setPadding(2, 0, 2, 0);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.setMarginStart(2);
        textView.setLayoutParams(textParams);
        buttonContainer.setFocusable(true);
        buttonContainer.setFocusableInTouchMode(true);
        buttonContainer.setBackgroundResource(R.drawable.jellyfin_button_minimal);
        buttonContainer.setOnClickListener(clickListener);
        buttonContainer.addView(button);
        buttonContainer.addView(textView);
        binding.toolBar.addView(buttonContainer);
        return button;
    }

    private void showSortMenu() {
        PopupMenu sortMenu = new PopupMenu(getActivity(), binding.toolBar, Gravity.END);
        for (Map.Entry<Integer, SortOption> entry : sortOptions.entrySet()) {
            SortOption option = entry.getValue();
            if (option.value != null) {
                MenuItem menuItem = sortMenu.getMenu().add(0, entry.getKey(), entry.getKey(), option.name);
                menuItem.setChecked(option.value.equals(mAdapter.getSortBy()) && option.order.equals(mAdapter.getSortOrder()));
            } else if (option.order != null) {
                MenuItem menuItem = sortMenu.getMenu().add(1, entry.getKey(), entry.getKey(), option.name);
                menuItem.setChecked(option.order.equals(mAdapter.getSortOrder()));
            } else {
                sortMenu.getMenu().add(2, entry.getKey(), entry.getKey(), option.name);
            }
        }
        sortMenu.getMenu().setGroupCheckable(0, true, true);
        sortMenu.getMenu().setGroupCheckable(1, true, true);
        sortMenu.getMenu().setGroupEnabled(2, false);
        sortMenu.setOnMenuItemClickListener(item -> {
            SortOption selectedOption = sortOptions.get(item.getItemId());
            if (selectedOption != null) {
                if (selectedOption.value != null) {
                    mAdapter.setSortBy(selectedOption);
                } else if (selectedOption.order != null) {
                    SortOption currentSort = getSortOption(mAdapter.getSortBy());
                    SortOption newSort = new SortOption(currentSort.name, currentSort.value, selectedOption.order);
                    mAdapter.setSortBy(newSort);
                }
                mAdapter.Retrieve();
                updateDisplayPrefs();
                return true;
            }
            return false;
        });
        sortMenu.show();
    }

    private void showGenreMenu() {
        PopupMenu genreMenu = new PopupMenu(getActivity(), binding.toolBar, Gravity.END);
        String[] genres = getResources().getStringArray(R.array.genres);
        genreMenu.getMenu().setGroupCheckable(0, true, true);
        String currentGenre = mAdapter.getGenreFilter();
        String normalizedCurrentGenre = currentGenre != null ? normalizeGenreName(currentGenre) : null;
        MenuItem allGenresItem = genreMenu.getMenu().add(0, 0, 0, getString(R.string.lbl_all_genres));
        allGenresItem.setChecked(normalizedCurrentGenre == null);
        for (int i = 0; i < genres.length; i++) {
            MenuItem item = genreMenu.getMenu().add(0, i + 1, i + 1, genres[i]);
            item.setCheckable(true);
            String normalizedGenre = normalizeGenreName(genres[i]);
            item.setChecked(normalizedGenre != null && normalizedGenre.equals(normalizedCurrentGenre));
        }
        genreMenu.setOnMenuItemClickListener(item -> {
            if (mAdapter != null) {
                String selectedGenre = item.getItemId() == 0 ? null : item.getTitle().toString();
                String normalizedGenre = selectedGenre != null ? normalizeGenreName(selectedGenre) : null;
                mAdapter.setGenreFilter(normalizedGenre);
                mAdapter.Retrieve();
                updateCounter(mAdapter.getTotalItems() > 0 ? 1 : 0);
            }
            return true;
        });
        genreMenu.show();
    }

    private void toggleUnwatchedFilter() {
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null) filters = new FilterOptions();
        filters.setUnwatchedOnly(!filters.isUnwatchedOnly());
        mUnwatchedButton.setActivated(filters.isUnwatchedOnly());
        mAdapter.setFilters(filters);
        mAdapter.Retrieve();
        updateDisplayPrefs();
    }

    private void toggleFavoriteFilter() {
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null) filters = new FilterOptions();
        filters.setFavoriteOnly(!filters.isFavoriteOnly());
        mFavoriteButton.setActivated(filters.isFavoriteOnly());
        mAdapter.setFilters(filters);
        mAdapter.Retrieve();
        updateDisplayPrefs();
    }

    private void openSettings() {
        boolean allowViewSelection = userViewsRepository.getValue().allowViewSelection(mFolder.getCollectionType());
        startActivity(ActivityDestinations.INSTANCE.displayPreferences(getContext(), mFolder.getDisplayPreferencesId(), allowViewSelection));
    }

    private void setupAlphabetSidebar() {
        mAlphabetSidebar = new VerticalAlphaPickerView(requireContext());
        mAlphabetSidebar.setOnAlphaSelected(letter -> {
            mAdapter.setStartLetter(letter.toString());
            loadGrid();
            return null;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        LinearLayout innerLayout = (LinearLayout) binding.alphabetSidebar.getRoot().getChildAt(0);
        innerLayout.addView(mAlphabetSidebar, params);
    }

    private String normalizeGenreName(String genre) {
        if (genre == null) return null;
        String normalized = genre.trim().toLowerCase();
        return GENRE_NORMALIZATION_CACHE.getOrDefault(normalized, normalized);
    }

    class JumplistPopup {
        private final int WIDTH = Utils.convertDpToPixel(requireContext(), 900);
        private final int HEIGHT = Utils.convertDpToPixel(requireContext(), 55);
        private final PopupWindow popupWindow;
        private final AlphaPickerView alphaPicker;
        JumplistPopup() {
            PopupEmptyBinding layout = PopupEmptyBinding.inflate(getLayoutInflater(), binding.rowsFragment, false);
            popupWindow = new PopupWindow(layout.emptyPopup, WIDTH, HEIGHT, true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setAnimationStyle(R.style.WindowAnimation_SlideTop);
            alphaPicker = new AlphaPickerView(requireContext(), null);
            alphaPicker.setOnAlphaSelected(letter -> {
                mAdapter.setStartLetter(letter.toString());
                loadGrid();
                dismiss();
                return null;
            });
            layout.emptyPopup.addView(alphaPicker);
        }
        public void show() {
            popupWindow.showAtLocation(binding.rowsFragment, Gravity.TOP, binding.rowsFragment.getLeft(), binding.rowsFragment.getTop());
            if (mAdapter.getStartLetter() != null && !mAdapter.getStartLetter().isEmpty() && alphaPicker != null && alphaPicker.isAttachedToWindow()) {
                alphaPicker.focus(mAdapter.getStartLetter().charAt(0));
            }
        }
        public void dismiss() {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        }
    }

    private void setupEventListeners() {
        if (mGridPresenter instanceof HorizontalGridPresenter)
            ((HorizontalGridPresenter) mGridPresenter).setOnItemViewClickedListener(mClickedListener);
        else if (mGridPresenter instanceof VerticalGridPresenter)
            ((VerticalGridPresenter) mGridPresenter).setOnItemViewClickedListener(mClickedListener);
        mClickedListener.registerListener(new ItemViewClickedListener());
        mSelectedListener.registerListener(new ItemViewSelectedListener());
        CoroutineUtils.readCustomMessagesOnLifecycle(getLifecycle(), customMessageRepository.getValue(), message -> {
            if (message.equals(CustomMessage.RefreshCurrentItem.INSTANCE)) refreshCurrentItem();
            return null;
        });
    }

    private void refreshCurrentItem() {
        if (mCurrentItem == null) return;
        ItemRowAdapterHelperKt.refreshItem(mAdapter, api.getValue(), this, mCurrentItem, () -> {
            if (mAdapter.getFilters() == null) return null;
            if ((mAdapter.getFilters().isFavoriteOnly() && !mCurrentItem.isFavorite()) || (mAdapter.getFilters().isUnwatchedOnly() && mCurrentItem.isPlayed())) {
                if (binding.toolBar != null && binding.toolBar.isAttachedToWindow()) {
                    binding.toolBar.requestFocus();
                }
                mAdapter.remove(mCurrentItem);
                mAdapter.setTotalItems(mAdapter.getTotalItems() - 1);
                int removedIndex = mAdapter.indexOf(mCurrentItem);
                updateCounter(removedIndex >= 0 ? removedIndex : 0);
            }
            return null;
        });
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (!(item instanceof BaseRowItem) || !isAdded()) return;
            itemLauncher.getValue().launch((BaseRowItem) item, mAdapter, requireContext());
        }
    }

    private final Runnable mDelayedSetItem = new Runnable() {
        @Override
        public void run() {
            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
            if (!(mGridPresenter instanceof HorizontalGridPresenter) && !(mGridPresenter instanceof VerticalGridPresenter)) {
                backgroundService.getValue().setBackground(mCurrentItem.getBaseItem());
            }
            setItem(mCurrentItem);
        }
    };

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            mHandler.removeCallbacks(mDelayedSetItem);
            if (!(item instanceof BaseRowItem)) {
                mCurrentItem = null;
                backgroundService.getValue().clearBackgrounds();
            } else {
                mCurrentItem = (BaseRowItem) item;
                if (!determiningPosterSize) {
                    int currentIndex = mAdapter.indexOf(mCurrentItem);
                    if (currentIndex >= 0) {
                        mAdapter.loadMoreItemsIfNeeded(currentIndex);
                    }
                }
            }
        }
    }
}
