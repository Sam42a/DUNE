package org.jellyfin.androidtv.ui.presentation;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import static android.view.View.FOCUS_DOWN;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.RatingType;
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior;
import org.jellyfin.androidtv.ui.card.InfoUnderSummaryCardView;
import org.jellyfin.androidtv.ui.card.LegacyImageCardView;
import org.jellyfin.androidtv.ui.itemhandling.AudioQueueBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.JellyfinImage;
import org.jellyfin.androidtv.util.apiclient.JellyfinImageKt;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.UserItemDataDto;
import org.koin.java.KoinJavaComponent;

import java.time.LocalDateTime;
import java.util.Locale;

import kotlin.Lazy;
import timber.log.Timber;
import coil3.ImageLoader;
import coil3.request.ImageRequest;

public class CardPresenter extends Presenter {
    private int mStaticHeight = 150;
    private ImageType mImageType = ImageType.POSTER;
    private double aspect;
    private boolean mShowInfo = true;
    private boolean isUserView = false;
    private boolean isUniformAspect = false;
    private boolean isHomeScreen = false;
    private boolean isListLayout = false; // New field to track List layout mode
    private final Lazy<ImageHelper> imageHelper = KoinJavaComponent.<ImageHelper>inject(ImageHelper.class);

    // Performance optimization: Cache ImageLoader instance
    private ImageLoader cachedImageLoader;

    // Performance optimization: Cache common drawables
    private Drawable cachedTilePortVideo;
    private Drawable cachedTileTv;
    private Drawable cachedFakeBlur;
    private Drawable cachedTilePortPerson;
    private Drawable cachedTileChapter;
    private Drawable cachedTileLandSeriesTimer;
    private Drawable cachedStarDrawable;

    public CardPresenter() {
        super();
    }

    public CardPresenter(boolean showInfo) {
        this();
        mShowInfo = showInfo;
    }

    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight) {
        this(showInfo, staticHeight);
        mImageType = imageType;
    }

    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight, boolean isListLayout) {
        this(showInfo, staticHeight);
        mImageType = imageType;
        this.isListLayout = isListLayout;
    }

    public CardPresenter(boolean showInfo, int staticHeight) {
        this(showInfo);
        mStaticHeight = staticHeight;
    }

    public void setHomeScreen(boolean isHomeScreen) {
        this.isHomeScreen = isHomeScreen;
    }

    public void setListLayout(boolean isListLayout) {
        this.isListLayout = isListLayout;
    }

    class ViewHolder extends Presenter.ViewHolder {
        private int cardWidth = 104; // 115 * 0.9
        private int cardHeight = 126; // 140 * 0.9
        private double aspect;
        private int mCardType;
        private ImageType mImageType;
        private boolean mIsListLayout;
        private boolean mShowInfo;
        private boolean mSelected;
        private boolean mFocusListenerSet = false;
        private boolean mMainImageUpdatePending = false;
        private volatile boolean hasPendingImageLoad = false;
        private volatile String currentImageUrl = null;
        private BaseCardView mCardView;
        private Drawable mDefaultCardImage;
        private BaseRowItem mItem;
        private boolean isUserView;

        public ViewHolder(View view) {
            super(view);

            // Store the card view as BaseCardView to handle both types
            mCardView = (BaseCardView) view;
            // Performance optimization: Use cached drawable from outer class
            mDefaultCardImage = CardPresenter.this.cachedTilePortVideo;
        }

        public int getCardWidth() {
            return cardWidth;
        }

        public int getCardHeight() {
            return cardHeight;
        }

        public void setItem(BaseRowItem m) {
            setItem(m, ImageType.POSTER, 130, 150, 150);
        }

        public void setItem(BaseRowItem m, ImageType imageType, int lHeight, int pHeight, int sHeight) {
            mItem = m;
            isUserView = false;
            switch (mItem.getBaseRowType()) {

                case BaseItem:
                    org.jellyfin.sdk.model.api.BaseItemDto itemDto = mItem.getBaseItem();
                    boolean showWatched = true;
                    boolean showProgress = false;
                    if (imageType != null && imageType.equals(ImageType.BANNER)) {
                        aspect = ImageHelper.ASPECT_RATIO_BANNER;
                    } else if (imageType != null && imageType.equals(ImageType.THUMB)) {
                        aspect = ImageHelper.ASPECT_RATIO_16_9;
                    } else {
                        aspect = imageHelper.getValue().getImageAspectRatio(itemDto, m.getPreferParentThumb());
                    }
                    switch (itemDto.getType()) {
                        case AUDIO:
                        case MUSIC_ALBUM:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_audio);
                            if (isUniformAspect) {
                                aspect = 1.0;
                            } else if (aspect < .8) {
                                aspect = 1.0;
                            }
                            showWatched = false;
                            break;
                        case PERSON:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_person);
                            break;
                        case MUSIC_ARTIST:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_person);
                            if (isUniformAspect) {
                                aspect = 1.0;
                            } else if (aspect < .8) {
                                aspect = 1.0;
                            }
                            showWatched = false;
                            break;
                        case SEASON:
                        case SERIES:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                            if (imageType != null && imageType.equals(ImageType.POSTER))
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                        case EPISODE:
                            if (m instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) m).getPreferSeriesPoster()) {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            } else {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                                aspect = ImageHelper.ASPECT_RATIO_16_9;
                                // Reduce size by 10% for home screen
                                cardWidth = (int)(cardWidth * 0.9);
                                cardHeight = (int)(cardHeight * 0.9);
                                if (isHomeScreen) {
                                    lHeight = (int)(lHeight * 0.8);
                                    pHeight = (int)(pHeight * 0.8);
                                    sHeight = (int)(sHeight * 0.8);
                                }
                                if (itemDto.getLocationType() != null) {
                                    switch (itemDto.getLocationType()) {
                                        case FILE_SYSTEM:
                                            break;
                                        case REMOTE:
                                            break;
                                        case VIRTUAL:
                                            if (mCardView instanceof LegacyImageCardView) {
                                                ((LegacyImageCardView) mCardView).setBanner(itemDto.getPremiereDate() == null || itemDto.getPremiereDate().isAfter(LocalDateTime.now()) ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                                            }
                                            break;
                                    }
                                }
                                showProgress = true;
                                //Always show info for episodes
                                mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            }
                            break;
                        case COLLECTION_FOLDER:
                        case USER_VIEW:
                            // Force the aspect ratio to 16x9 because the server is returning the wrong value of 1
                            // When this is fixed we should still force 16x9 if an image is not set to be consistent
                            aspect = ImageHelper.ASPECT_RATIO_16_9;
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                            isUserView = true;
                            break;
                        case FOLDER:
                        case GENRE:
                        case MUSIC_GENRE:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_folder);
                            break;
                        case PHOTO:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_photo);
                            showWatched = false;
                            break;
                        case PHOTO_ALBUM:
                        case PLAYLIST:
                            showWatched = false;
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                            break;
                        case MOVIE:
                        case VIDEO:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                            showProgress = true;
                            if (imageType != null && imageType.equals(ImageType.POSTER))
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                        default:
                            mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.fakeblur);
                            if (imageType != null && imageType.equals(ImageType.POSTER))
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            break;
                    }
                    cardHeight = !m.getStaticHeight() ? (aspect > 1 ? lHeight : pHeight) : sHeight;
                    cardWidth = (int) (aspect * cardHeight);
                    
                    // For List layout, use specific dimensions for each image type
                    if (isListLayout && mImageType != null) {
                        switch (mImageType) {
                            case POSTER:
                                cardWidth = 190;
                                cardHeight = (int) Math.round(cardWidth / aspect);
                                break;
                            case BANNER:
                                cardWidth = 90;
                                cardHeight = (int) Math.round(cardWidth / aspect);
                                break;
                            case THUMB:
                                cardWidth = 300;
                                cardHeight = (int) Math.round(cardWidth / aspect);
                                break;
                            default:
                                // Use calculated dimensions for other types
                                break;
                        }
                    }
                    
                    if (cardWidth < 5) {
                        cardWidth = 115;  //Guard against zero size images causing picasso to barf
                    }
                    if (Utils.isTrue(itemDto.isPlaceHolder())) {
                        if (mCardView instanceof LegacyImageCardView) {
                            ((LegacyImageCardView) mCardView).setBanner(R.drawable.banner_edge_disc);
                        }
                    }
                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {
                        WatchedIndicatorBehavior showIndicator = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getWatchedIndicatorBehavior());
                        if (userData.getPlayed()) {
                            if (showIndicator != WatchedIndicatorBehavior.NEVER && (showIndicator != WatchedIndicatorBehavior.EPISODES_ONLY || itemDto.getType() == BaseItemKind.EPISODE)) {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).setUnwatchedCount(0);
                                }
                            } else {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).setUnwatchedCount(-1);
                                }
                            }
                        } else if (userData.getUnplayedItemCount() != null) {
                            if (userData.getUnplayedItemCount() > 0 && showIndicator != WatchedIndicatorBehavior.NEVER) {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).setUnwatchedCount(userData.getUnplayedItemCount());
                                }
                            } else {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).setUnwatchedCount(-1);
                                }
                            }
                        } else {
                            if (mCardView instanceof LegacyImageCardView) {
                                ((LegacyImageCardView) mCardView).setUnwatchedCount(-1);
                            }
                        }
                    }

                    if (showProgress && itemDto != null && userData != null) {
                        Long runTimeTicks = itemDto.getRunTimeTicks();
                        Long playbackPositionTicks = userData.getPlaybackPositionTicks();
                        if (runTimeTicks != null && runTimeTicks > 0 && playbackPositionTicks != null && playbackPositionTicks > 0) {
                            if (mCardView instanceof LegacyImageCardView) {
                                ((LegacyImageCardView) mCardView).setProgress(((int) (userData.getPlaybackPositionTicks() * 100.0 / itemDto.getRunTimeTicks()))); // force floating pt math with 100.0
                            }
                        } else {
                            if (mCardView instanceof LegacyImageCardView) {
                                ((LegacyImageCardView) mCardView).setProgress(0);
                            }
                        }
                    }
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    } else if (mCardView instanceof InfoUnderSummaryCardView && isListLayout) {
                        ((InfoUnderSummaryCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    break;
                case LiveTvChannel:
                    org.jellyfin.sdk.model.api.BaseItemDto channel = mItem.getBaseItem();
                    // TODO: Is it even possible to have channels with banners or thumbs?
                    double tvAspect = (imageType != null && imageType.equals(ImageType.BANNER)) ? ImageHelper.ASPECT_RATIO_BANNER :
                        (imageType != null && imageType.equals(ImageType.THUMB)) ? ImageHelper.ASPECT_RATIO_16_9 :
                        Utils.getSafeValue(channel.getPrimaryImageAspectRatio(), 1.0);
                    cardHeight = !m.getStaticHeight() ? tvAspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int) ((tvAspect) * cardHeight);
                    if (cardWidth < 5) {
                        cardWidth = 115;  //Guard against zero size images causing picasso to barf
                    }
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                        // Channel logos should fit within the view
                        ((LegacyImageCardView) mCardView).getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
                    } else if (mCardView instanceof InfoUnderSummaryCardView && isListLayout) {
                        ((InfoUnderSummaryCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_tv);
                    break;
                case LiveTvProgram:
                    org.jellyfin.sdk.model.api.BaseItemDto program = mItem.getBaseItem();
                    if (program.getLocationType() != null) {
                        switch (program.getLocationType()) {
                            case FILE_SYSTEM:
                            case REMOTE:
                                break;
                            case VIRTUAL:
                                if (program.getStartDate() != null && program.getStartDate().isAfter(LocalDateTime.now())) {
                                    if (mCardView instanceof LegacyImageCardView) {
                                        ((LegacyImageCardView) mCardView).setBanner(R.drawable.banner_edge_future);
                                    }
                                }
                                break;
                        }
                    }
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(192, 129, ImageView.ScaleType.CENTER_INSIDE);
                    } else if (mCardView instanceof InfoUnderSummaryCardView && isListLayout) {
                        ((InfoUnderSummaryCardView) mCardView).setMainImageDimensions(192, 129);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedFakeBlur;
                    //Always show info for programs
                    mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                    break;
                case LiveTvRecording:
                    BaseItemDto recording = mItem.getBaseItem();
                    double recordingAspect = (imageType != null && imageType.equals(ImageType.BANNER)) ? ImageHelper.ASPECT_RATIO_BANNER : ((imageType != null && imageType.equals(ImageType.THUMB)) ? ImageHelper.ASPECT_RATIO_16_9 : Utils.getSafeValue(recording.getPrimaryImageAspectRatio(), ImageHelper.ASPECT_RATIO_7_9));
                    cardHeight = !m.getStaticHeight() ? recordingAspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int) ((recordingAspect) * cardHeight);
                    if (cardWidth < 5) {
                        cardWidth = 115;  //Guard against zero size images causing picasso to barf
                    }
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    } else if (mCardView instanceof InfoUnderSummaryCardView && isListLayout) {
                        ((InfoUnderSummaryCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedTileTv;
                    break;
                case Person:
                    cardHeight = !m.getStaticHeight() ? pHeight : sHeight;
                    cardWidth = (int) (ImageHelper.ASPECT_RATIO_7_9 * cardHeight);
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedTilePortPerson;
                    break;
                case Chapter:
                    cardHeight = !m.getStaticHeight() ? pHeight : sHeight;
                    cardWidth = (int) (ImageHelper.ASPECT_RATIO_16_9 * cardHeight);
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedTileChapter;
                    break;
                case GridButton:
                    cardHeight = !m.getStaticHeight() ? pHeight : sHeight;
                    cardWidth = (int) (ImageHelper.ASPECT_RATIO_7_9 * cardHeight);
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedTilePortVideo;
                    break;
                case SeriesTimer:
                    cardHeight = !m.getStaticHeight() ? pHeight : sHeight;
                    cardWidth = (int) (ImageHelper.ASPECT_RATIO_16_9 * cardHeight);
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).setMainImageDimensions(cardWidth, cardHeight);
                    }
                    mDefaultCardImage = CardPresenter.this.cachedTileLandSeriesTimer;
                    //Always show info for timers
                    mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                    break;
            }
        }

        public BaseRowItem getItem() {
            return mItem;
        }

        protected void updateCardViewImage(@Nullable String url, @Nullable String blurHash) {
            if (hasPendingImageLoad) {
                if (mCardView instanceof LegacyImageCardView) {
                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(null);
                } else if (mCardView instanceof InfoUnderSummaryCardView) {
                    ((InfoUnderSummaryCardView) mCardView).getMainImageView().setImageDrawable(null);
                }
                hasPendingImageLoad = false;
            }

            if (url != null && !url.equals(currentImageUrl)) {
                currentImageUrl = url;
                hasPendingImageLoad = true;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) mCardView).getMainImageView().load(url, blurHash, mDefaultCardImage, aspect, 32);
                    } else if (mCardView instanceof InfoUnderSummaryCardView) {
                        ((InfoUnderSummaryCardView) mCardView).getMainImageView().load(url, blurHash, mDefaultCardImage, aspect, 32);
                    }
                } else {
                    loadImageCompat(url, mDefaultCardImage);
                }

                // Performance optimization: Use shorter delay and more efficient cleanup
                mCardView.postDelayed(() -> {
                    if (url.equals(currentImageUrl)) {
                        hasPendingImageLoad = false;
                    }
                }, 25); // Reduced from 50ms to 25ms
            } else {
                if (mCardView instanceof LegacyImageCardView) {
                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(mDefaultCardImage);
                } else if (mCardView instanceof InfoUnderSummaryCardView) {
                    ((InfoUnderSummaryCardView) mCardView).getMainImageView().setImageDrawable(mDefaultCardImage);
                }
            }
        }

        /**
         * Compatibility method for loading images on API 21-22
         */
        private void loadImageCompat(@Nullable String url, @Nullable Drawable placeholder) {
            if (url == null) {
                if (mCardView instanceof LegacyImageCardView) {
                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(placeholder);
                }
                return;
            }

            // Performance optimization:  cached ImageLoader
            try {
                coil3.request.ImageRequest request = new coil3.request.ImageRequest.Builder(mCardView.getContext())
                    .data(url)
                    .target(
                        /* onStart */ (coil3.Image placeholderImage) -> {
                            if (placeholder != null) {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(placeholder);
                                }
                            }
                            return null;
                        },
                        /* onSuccess */ (coil3.Image image) -> {
                            if (mCardView instanceof LegacyImageCardView) {
                                ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable((android.graphics.drawable.Drawable) image);
                            }
                            return null;
                        },
                        /* onError */ (coil3.Image error) -> {
                            if (placeholder != null) {
                                if (mCardView instanceof LegacyImageCardView) {
                                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(placeholder);
                                }
                            }
                            return null;
                        }
                    )
                    .build();

                cachedImageLoader.enqueue(request);
            } catch (Exception e) {
                // Fallback to placeholder if image loading fails
                if (mCardView instanceof LegacyImageCardView) {
                    ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(placeholder);
                }
                Timber.tag("CardPresenter").e(e, "Error loading image compat");
            }
        }

        protected void resetCardView() {
            if (mCardView instanceof LegacyImageCardView) {
                ((LegacyImageCardView) mCardView).clearBanner();
                ((LegacyImageCardView) mCardView).setUnwatchedCount(-1);
                ((LegacyImageCardView) mCardView).setProgress(0);
                ((LegacyImageCardView) mCardView).setRating(null);
                ((LegacyImageCardView) mCardView).setBadgeImage(null);
            }

            // Cancel any pending image load and clear image
            hasPendingImageLoad = false;
            currentImageUrl = null;
            if (mCardView instanceof LegacyImageCardView) {
                ((LegacyImageCardView) mCardView).getMainImageView().setImageDrawable(null);
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        initializeCachedResources(parent.getContext());

        BaseCardView cardView;
        
        // Use different card view based on layout mode
        if (isListLayout) {
            // For List layout, use InfoUnderSummaryCardView with horizontal layout
            cardView = new InfoUnderSummaryCardView(parent.getContext());
            cardView.setCardType(InfoUnderSummaryCardView.CARD_TYPE_INFO_UNDER_SUMMARY);
        } else {
            // For other layouts, use LegacyImageCardView
            LegacyImageCardView legacyCardView = new LegacyImageCardView(parent.getContext(), mShowInfo);
            if (!mShowInfo) {
                // For non-List layout with showInfo=false, use MAIN_ONLY
                legacyCardView.setCardType(BaseCardView.CARD_TYPE_MAIN_ONLY);
            }
            cardView = legacyCardView;
        }
        
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = parent.getContext().getTheme();
        theme.resolveAttribute(R.attr.cardViewBackground, typedValue, true);
        @ColorInt int color = typedValue.data;
        cardView.setBackgroundColor(color);

        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (!(item instanceof BaseRowItem)) {
            return;
        }
        BaseRowItem rowItem = (BaseRowItem) item;

        ViewHolder holder = (ViewHolder) viewHolder;
        holder.hasPendingImageLoad = false;
        holder.currentImageUrl = null;
        // Initialize holder fields from presenter
        holder.aspect = aspect;
        holder.isUserView = isUserView;
        // Set basic card properties first
        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        if (holder.mCardView != null) {
            // Set focus properties once
            holder.mCardView.setFocusable(true);
            holder.mCardView.setFocusableInTouchMode(true);
            holder.mCardView.setElevation(0);
            holder.mCardView.setSelected(false);

            // Handle different card types
            if (holder.mCardView instanceof InfoUnderSummaryCardView) {
                // Use InfoUnderSummaryCardView specific methods
                InfoUnderSummaryCardView infoCardView = (InfoUnderSummaryCardView) holder.mCardView;
                
                // Set title, summary, and ratings
                infoCardView.setTitle(rowItem.getCardName(infoCardView.getContext()));
                infoCardView.setSummary(rowItem.getSummary(infoCardView.getContext()) != null ? rowItem.getSummary(infoCardView.getContext()) : "");
                
                BaseItemDto itemDto = rowItem.getBaseItem();
                if (itemDto != null) {
                    infoCardView.setCommunityRating(itemDto.getCommunityRating());
                    infoCardView.setCriticRating(itemDto.getCriticRating());
                    infoCardView.setYear(itemDto.getProductionYear());
                    infoCardView.setDuration(itemDto.getRunTimeTicks());
                    
                    // Set watched indicator
                    if (itemDto.getUserData() != null) {
                        boolean watched = itemDto.getUserData().getPlayed();
                        int unwatchedCount = itemDto.getUserData().getUnplayedItemCount() != null ? 
                            itemDto.getUserData().getUnplayedItemCount() : 0;
                        infoCardView.setWatchedIndicator(watched, unwatchedCount);
                        
                        // Set resume progress
                        if (itemDto.getUserData() != null) {
                            Long runTimeTicks = itemDto.getRunTimeTicks();
                            Long playbackPositionTicks = itemDto.getUserData().getPlaybackPositionTicks();
                            if (runTimeTicks != null && runTimeTicks > 0 && playbackPositionTicks != null && playbackPositionTicks > 0) {
                                int progress = (int) ((itemDto.getUserData().getPlaybackPositionTicks() * 100) / itemDto.getRunTimeTicks());
                                infoCardView.setResumeProgress(progress);
                            }
                        }
                    }
                }
            } else if (holder.mCardView instanceof LegacyImageCardView) {
                // Use LegacyImageCardView methods for other card types
                LegacyImageCardView legacyCardView = (LegacyImageCardView) holder.mCardView;
                legacyCardView.setTitleText(rowItem.getCardName(legacyCardView.getContext()));
                legacyCardView.setContentText(rowItem.getSubText(legacyCardView.getContext()));
                legacyCardView.showFavIcon(rowItem.isFavorite());
                if (rowItem instanceof AudioQueueBaseRowItem && ((AudioQueueBaseRowItem) rowItem).getPlaying()) {
                    legacyCardView.setPlayingIndicator(true);
                } else {
                    legacyCardView.setPlayingIndicator(false);
                }
            }

            // Set overlay info for posters
            if (ImageType.POSTER.equals(mImageType)) {
                if (holder.mCardView instanceof LegacyImageCardView) {
                    ((LegacyImageCardView) holder.mCardView).setOverlayInfo(rowItem);
                }
            }
            // Set overlay info for all image types in List layout (only for LegacyImageCardView)
            if (isListLayout && holder.mCardView instanceof LegacyImageCardView) {
                ((LegacyImageCardView) holder.mCardView).setOverlayInfo(rowItem);
            }
            // Set up optimized focus handling only once per view holder
            setupFocusHandling(holder);

            // Lazy load rating and badge information
            loadRatingAndBadgeAsync(holder, rowItem);

            // Lazy load image
            loadImageAsync(holder, rowItem);
        }
    }
    private void setupFocusHandling(ViewHolder holder) {
        // Only set up focus handling once per view holder to avoid duplicate listeners
        if (holder.mFocusListenerSet) {
            return;
        }
        // Set minimal elevation for focus effects
        final float minElevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            0.1f,
            holder.mCardView.getContext().getResources().getDisplayMetrics()
        );

        // Set up a lightweight focus change listener
        holder.mCardView.setOnFocusChangeListener((v, hasFocus) -> {
            v.setSelected(hasFocus);

            // Apply minimal elevation when focused
            if (hasFocus) {
                v.setElevation(minElevation);
                v.setTranslationZ(minElevation);
            } else {
                v.setElevation(0);
                v.setTranslationZ(0);
            }

            // Set border drawable with API level check
            View mainImage = v.findViewById(R.id.main_image);
            if (mainImage != null) {
                if (hasFocus) {
                    Drawable border = ContextCompat.getDrawable(
                        v.getContext(),
                        R.drawable.card_focused_border
                    );
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mainImage.setForeground(border);
                    } else {
                        mainImage.setBackground(border);
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mainImage.setForeground(null);
                    } else {
                        mainImage.setBackground(null);
                    }
                }
            }
        });
        holder.mFocusListenerSet = true;
    }
    private void loadRatingAndBadgeAsync(ViewHolder holder, BaseRowItem rowItem) {
        // Use a lightweight approach for rating and badge loading
        if (rowItem.getBaseItem() != null && rowItem.getBaseItem().getType() != BaseItemKind.USER_VIEW) {
            RatingType ratingType = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getDefaultRatingType());
            if (ratingType == RatingType.RATING_TOMATOES) {
                // Load badge asynchronously
                holder.mCardView.post(() -> {
                    Drawable badge = rowItem.getBadgeImage(holder.view.getContext(), imageHelper.getValue());
                    if (badge != null && holder.mCardView instanceof LegacyImageCardView) {
                        ((LegacyImageCardView) holder.mCardView).setBadgeImage(badge);
                    }
                });
            } else if (ratingType == RatingType.RATING_STARS &&
                       rowItem.getBaseItem().getCommunityRating() != null) {
                // Set star badge and rating
                holder.mCardView.post(() -> {
                    if (holder.mCardView instanceof LegacyImageCardView) {
                        LegacyImageCardView legacyCardView = (LegacyImageCardView) holder.mCardView;
                        legacyCardView.setBadgeImage(CardPresenter.this.cachedStarDrawable);
                        legacyCardView.setRating(String.format(Locale.US, "%.1f", rowItem.getBaseItem().getCommunityRating()));
                    }
                });
            }
        }
    }
    private void loadImageAsync(ViewHolder holder, BaseRowItem rowItem) {
        // Post image loading to avoid blocking the main thread
        holder.mCardView.post(() -> {
            try {
                JellyfinImage image = null;
                if (rowItem.getBaseItem() != null) {
                    if (holder.aspect == ImageHelper.ASPECT_RATIO_BANNER) {
                        image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.BANNER);
                    } else if (holder.aspect == ImageHelper.ASPECT_RATIO_2_3 && rowItem.getBaseItem().getType() == BaseItemKind.EPISODE && rowItem instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) rowItem).getPreferSeriesPoster()) {
                        image = JellyfinImageKt.getSeriesPrimaryImage(rowItem.getBaseItem());
                    } else if (holder.aspect == ImageHelper.ASPECT_RATIO_16_9 && !holder.isUserView && (rowItem.getBaseItem().getType() != BaseItemKind.EPISODE || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY) || (rowItem.getPreferParentThumb() && rowItem.getBaseItem().getParentThumbImageTag() != null))) {
                        if (rowItem.getPreferParentThumb() || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY)) {
                            image = JellyfinImageKt.getParentImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                        } else {
                            image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                        }
                    } else {
                        image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
                    }
                }

                int fillWidth = Math.round(holder.getCardWidth() * holder.mCardView.getResources().getDisplayMetrics().density);
                int fillHeight = Math.round(holder.getCardHeight() * holder.mCardView.getResources().getDisplayMetrics().density);

                holder.updateCardViewImage(
                    image == null ? rowItem.getImageUrl(holder.mCardView.getContext(), imageHelper.getValue(), mImageType != null ? mImageType : ImageType.POSTER, fillWidth, fillHeight) : imageHelper.getValue().getImageUrl(image),
                    image == null ? null : image.getBlurHash()
                );
            } catch (Exception e) {
                // Log error
                Timber.tag("CardPresenter").e(e, "Error loading image");
            }
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder) viewHolder).resetCardView();
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {
    }

    public void setUniformAspect(boolean uniformAspect) {
        isUniformAspect = uniformAspect;
    }

    private void initializeCachedResources(android.content.Context context) {
        if (cachedImageLoader == null) {
            // ImageLoader instance
            cachedImageLoader = new coil3.ImageLoader.Builder(context).build();

            // common drawables
            cachedTilePortVideo = ContextCompat.getDrawable(context, R.drawable.tile_port_video);
            cachedTileTv = ContextCompat.getDrawable(context, R.drawable.tile_tv);
            cachedFakeBlur = ContextCompat.getDrawable(context, R.drawable.fakeblur);
            cachedTilePortPerson = ContextCompat.getDrawable(context, R.drawable.tile_port_person);
            cachedTileChapter = ContextCompat.getDrawable(context, R.drawable.tile_chapter);
            cachedTileLandSeriesTimer = ContextCompat.getDrawable(context, R.drawable.tile_land_series_timer);
            cachedStarDrawable = ContextCompat.getDrawable(context, R.drawable.ic_star);
        }
    }
}
