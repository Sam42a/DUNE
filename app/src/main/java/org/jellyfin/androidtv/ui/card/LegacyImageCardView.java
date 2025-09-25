package org.jellyfin.androidtv.ui.card;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.databinding.ViewCardLegacyImageBinding;
import org.jellyfin.androidtv.ui.AsyncImageView;
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.util.ContextExtensionsKt;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.jellyfin.androidtv.util.Utils;

import java.text.NumberFormat;

import timber.log.Timber;

/**
 * Modified ImageCard with no fade on the badge
 * A card view with an {@link ImageView} as its main region.
 */
public class LegacyImageCardView extends BaseCardView implements androidx.lifecycle.LifecycleObserver {
    private ViewCardLegacyImageBinding binding = ViewCardLegacyImageBinding.inflate(LayoutInflater.from(getContext()), this);
    private ImageView mBanner;
    private int BANNER_SIZE = Utils.convertDpToPixel(getContext(), 50);
    private int noIconMargin = Utils.convertDpToPixel(getContext(), 5);
    private NumberFormat nf = NumberFormat.getInstance();

    private float defaultScale = 1.0f;
    private float focusScale = 1.0f;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Load scale values from resources

        // Apply default scale
        setScaleX(defaultScale);
        setScaleY(defaultScale);

        // Ensure no elevation is set after inflation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0);
            setTranslationZ(0);
            setStateListAnimator(null);
        }
    }

    public LegacyImageCardView(Context context, boolean showInfo) {
        super(context, null, androidx.leanback.R.attr.imageCardViewStyle);

        // Completely disable all elevation animations
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Create a custom StateListAnimator that does nothing
            android.animation.StateListAnimator stateListAnimator = new android.animation.StateListAnimator();

            // Add a default state with no animation
            stateListAnimator.addState(new int[0], android.animation.AnimatorInflater.loadAnimator(context, android.R.animator.fade_in));

            // Apply the custom StateListAnimator
            setStateListAnimator(stateListAnimator);

            // Explicitly set elevation to 0
            setElevation(0);
            setTranslationZ(0);

            // Disable outline provider
            setOutlineProvider(null);
        }

        // Also set important for accessibility to false to prevent any elevation changes
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Disable clip to padding and clip children to prevent any clipping issues
        setClipToPadding(false);
        setClipChildren(false);

        if (!showInfo) {
            setCardType(CARD_TYPE_MAIN_ONLY);
        }

        // "hack" to trigger KeyProcessor to open the menu for this item on long press
        setOnLongClickListener(v -> {
            Activity activity = ContextExtensionsKt.getActivity(getContext());
            if (activity == null) return false;
            // Make sure the view is focused so the created menu uses it as anchor
            if (!v.requestFocus()) return false;
            return activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        });

        setForeground(null);

        // Add focus/selection listener for border
        setOnFocusChangeListener((v, hasFocus) -> {
            Timber.d("Focus changed: %s", hasFocus);
            updateCardBorder();
        });
        setFocusable(true);
        setFocusableInTouchMode(true);

        // Set click listener for selection (if needed)
        setOnClickListener(v -> {
            setSelected(!isSelected());
            Timber.d("Clicked, selected: %s", isSelected());
            updateCardBorder();
        });
    }

    private boolean isFocusedState = false;

    @SuppressLint({"NewApi", "WrongConstant"})
    private void updateCardBorder() {
        // Always show white borders when focused or selected
        boolean shouldShowBorder = isFocused() || isSelected();

        Timber.d("Card border state - focused: %s, selected: %s, showing border: %s",
                isFocused(), isSelected(), shouldShowBorder);

        // Skip if state hasn't changed
        if (isFocusedState == shouldShowBorder) return;
        isFocusedState = shouldShowBorder;

        if (shouldShowBorder) {
            // Apply border to the main image view with proper insets
            Drawable border = ContextCompat.getDrawable(getContext(), R.drawable.card_focused_border);
            int padding = (int) getContext().getResources().getDimension(R.dimen.card_border_padding);

            // Set the border as the foreground of the main image
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                binding.mainImage.setForeground(border);
            } else {
                // For API < 23, use a different approach with padding and background
                binding.mainImage.setPadding(padding, padding, padding, padding);
                binding.mainImage.setBackground(border);
            }
        } else {
            // Remove border
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                binding.mainImage.setForeground(null);
            } else {
                binding.mainImage.setPadding(0, 0, 0, 0);
                binding.mainImage.setBackground(null);
            }
        }

        // Invalidate the view to ensure the border is redrawn
        binding.mainImage.invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        // Apply scale based on focus
        if (gainFocus) {
            animate().scaleX(focusScale).scaleY(focusScale).setDuration(150).start();
        } else {
            animate().scaleX(defaultScale).scaleY(defaultScale).setDuration(150).start();
        }

        // Store current elevation
        float currentElevation = getElevation();

        // Call super
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        // Restore elevation to 0 and update border
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0);
            setTranslationZ(0);
        }

        updateCardBorder();
    }

    @Override
    public void setSelected(boolean selected) {
        // Store current state
        boolean wasSelected = isSelected();

        // Call super
        super.setSelected(selected);

        // Only update the border if the selection state actually changed
        if (wasSelected != selected) {
            updateCardBorder();
        }

        // Ensure no elevation is set and no animations are running
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Clear any running animations
            clearAnimation();

            // Reset elevation and translation
            setElevation(0);
            setTranslationZ(0);

            // Ensure no outline provider is set
            setOutlineProvider(null);

            // Force a redraw
            invalidateOutline();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Reset scale to default when reattached
        setScaleX(defaultScale);
        setScaleY(defaultScale);

        // Disable elevation in parent if it's a view group
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                ((ViewGroup) parent).setClipToPadding(false);
                ((ViewGroup) parent).setClipChildren(false);
                ((ViewGroup) parent).setElevation(0);
                ((ViewGroup) parent).setTranslationZ(0);
                ((ViewGroup) parent).setStateListAnimator(null);
            }
        }

        // Ensure no outline provider is set
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(null);
            invalidateOutline();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Reset scale when detached to prevent animation issues
        setScaleX(1.0f);
        setScaleY(1.0f);
        super.onDetachedFromWindow();
    }

    @androidx.lifecycle.OnLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
    public void onResume() {
        // Update border state when preferences might have changed
        updateCardBorder();
    }

    public void setBanner(int bannerResource) {
        if (mBanner == null) {
            mBanner = new ImageView(getContext());
            mBanner.setLayoutParams(new ViewGroup.LayoutParams(BANNER_SIZE, BANNER_SIZE));

            ((ViewGroup) getRootView()).addView(mBanner);
        }

        mBanner.setImageResource(bannerResource);
        mBanner.setVisibility(VISIBLE);
    }

    public final AsyncImageView getMainImageView() {
        return binding.mainImage;
    }

    public void setPlayingIndicator(boolean playing) {
        if (playing) {
            // TODO use decent animation for equalizer icon
            binding.extraBadge.setBackgroundResource(R.drawable.ic_play);
            binding.extraBadge.setVisibility(VISIBLE);
        } else {
            binding.extraBadge.setBackgroundResource(R.drawable.blank10x10);
        }
    }

    public void setMainImageDimensions(int width, int height) {
        setMainImageDimensions(width, height, ImageView.ScaleType.CENTER_CROP);
    }

    public void setMainImageDimensions(int width, int height, ImageView.ScaleType scaleType) {
        ViewGroup.LayoutParams lp = binding.mainImage.getLayoutParams();
        lp.width = Math.round(width * getResources().getDisplayMetrics().density);
        lp.height = Math.round(height * getResources().getDisplayMetrics().density);
        binding.mainImage.setLayoutParams(lp);
        binding.mainImage.setScaleType(scaleType);
        if (mBanner != null) mBanner.setX(lp.width - BANNER_SIZE);
        ViewGroup.LayoutParams lp2 = binding.resumeProgress.getLayoutParams();
        lp2.width = lp.width;
        binding.resumeProgress.setLayoutParams(lp2);

        // Ensure the outline provider is updated with the new dimensions
        if (binding.mainImage.getOutlineProvider() != null) {
            binding.mainImage.invalidateOutline();
        }
    }

    public void setTitleText(CharSequence text) {
        if (binding.title == null) {
            return;
        }

        binding.title.setText(text);
        setTextMaxLines();
    }

    public void setOverlayText(String text) {
        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY) {
            binding.overlayText.setText(text);
            binding.nameOverlay.setVisibility(VISIBLE);
        } else {
            binding.nameOverlay.setVisibility(GONE);
        }
    }

    public void setOverlayInfo(BaseRowItem item) {
        if (binding.overlayText == null) return;

        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY && item.getShowCardInfoOverlay()) {
            switch (item.getBaseItem().getType()) {
                case PHOTO:
                    insertCardData(item.getBaseItem().getPremiereDate() != null ? DateTimeExtensionsKt.getDateFormatter(getContext()).format(item.getBaseItem().getPremiereDate()) : item.getFullName(getContext()), R.drawable.ic_camera, true);
                    break;
                case PHOTO_ALBUM:
                    insertCardData(item.getFullName(getContext()), R.drawable.ic_photos, true);
                    break;
                case VIDEO:
                    insertCardData(item.getFullName(getContext()), R.drawable.ic_movie, true);
                    break;
                case FOLDER:
                    insertCardData(item.getFullName(getContext()), R.drawable.ic_folder, true);
                    break;
                case PLAYLIST:
                case MUSIC_ARTIST:
                case PERSON:
                default:
                    binding.overlayText.setText(item.getFullName(getContext()));
                    break;
            }
            if (item instanceof BaseItemDtoBaseRowItem) {
                binding.overlayCount.setText(((BaseItemDtoBaseRowItem) item).getChildCountStr());
            } else {
                binding.overlayCount.setText(null);
            }
            binding.nameOverlay.setVisibility(VISIBLE);
        }
    }

    public void insertCardData (@Nullable String fullName, @NonNull int icon, @NonNull boolean iconVisible) {
        binding.overlayText.setText(fullName);
        if (iconVisible) {
            binding.iconImage.setImageResource(icon);
            binding.icon.setVisibility(VISIBLE);
        }
    }

    public CharSequence getTitle() {
        if (binding.title == null) {
            return null;
        }

        return binding.title.getText();
    }

    public void setContentText(CharSequence text) {
        if (binding.contentText == null) {
            return;
        }

        binding.contentText.setText(text);
        setTextMaxLines();
    }

    public CharSequence getContentText() {
        if (binding.contentText == null) {
            return null;
        }

        return binding.contentText.getText();
    }

    public void setRating(String rating) {
        if (rating != null) {
            binding.badgeText.setText(rating);
            binding.badgeText.setVisibility(VISIBLE);
        } else {
            binding.badgeText.setText("");
            binding.badgeText.setVisibility(GONE);
        }
    }

    public void setBadgeImage(Drawable drawable) {
        if (binding.extraBadge == null) {
            return;
        }

        if (drawable != null) {
            binding.extraBadge.setImageDrawable(drawable);
            binding.extraBadge.setVisibility(View.VISIBLE);
        } else {
            binding.extraBadge.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void setTextMaxLines() {
        if (TextUtils.isEmpty(getTitle())) {
            binding.contentText.setMaxLines(2);
        } else {
            binding.contentText.setMaxLines(1);
        }

        if (TextUtils.isEmpty(getContentText())) {
            binding.title.setMaxLines(2);
        } else {
            binding.title.setMaxLines(1);
        }
    }

    public void clearBanner() {
        if (mBanner != null) {
            mBanner.setVisibility(GONE);
        }
    }

    public void setUnwatchedCount(int count) {
        if (count > 0) {
            binding.unwatchedCount.setText(count > 99 ? getContext().getString(R.string.watch_count_overflow) : nf.format(count));
            binding.unwatchedCount.setVisibility(VISIBLE);
            binding.checkMark.setVisibility(INVISIBLE);
            binding.watchedIndicator.setVisibility(VISIBLE);
        } else if (count == 0) {
            binding.checkMark.setVisibility(VISIBLE);
            binding.unwatchedCount.setVisibility(INVISIBLE);
            binding.watchedIndicator.setVisibility(VISIBLE);
        } else {
            binding.watchedIndicator.setVisibility(GONE);
        }
    }

    public void setProgress(int pct) {
        if (pct > 0) {
            binding.resumeProgress.setProgress(pct);
            binding.resumeProgress.setVisibility(VISIBLE);
        } else {
            binding.resumeProgress.setVisibility(GONE);
        }
    }

    public void showFavIcon(boolean show) {
        binding.favIcon.setVisibility(show ? VISIBLE : GONE);
    }
}
