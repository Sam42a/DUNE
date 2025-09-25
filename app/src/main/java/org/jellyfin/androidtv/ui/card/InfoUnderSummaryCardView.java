package org.jellyfin.androidtv.ui.card;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.ui.AsyncImageView;
import org.jellyfin.androidtv.util.ContextExtensionsKt;
import org.jellyfin.androidtv.util.Utils;

import java.text.NumberFormat;

import timber.log.Timber;

public class InfoUnderSummaryCardView extends BaseCardView implements androidx.lifecycle.LifecycleObserver {

    // Custom card type constant for INFO_UNDER_SUMMARY
    public static final int CARD_TYPE_INFO_UNDER_SUMMARY = 1000;
    private AsyncImageView mainImage;
    private TextView title;
    private TextView summary;
    private TextView communityRating;
    private TextView criticRating;
    private TextView year;
    private TextView duration;
    private ProgressBar resumeProgress;
    private FrameLayout watchedIndicator;
    private TextView unwatchedCount;
    private ImageView checkMark;
    private ImageView mBanner;
    private int BANNER_SIZE = Utils.convertDpToPixel(getContext(), 50);
    private NumberFormat nf = NumberFormat.getInstance();

    private float defaultScale = 1.0f;
    private float focusScale = 1.0f;

    public InfoUnderSummaryCardView(Context context) {
        super(context, null, androidx.leanback.R.attr.imageCardViewStyle);

        LayoutInflater.from(context).inflate(R.layout.view_card_info_under_summary, this, true);

        // Find views by ID
        mainImage = findViewById(R.id.main_image);
        title = findViewById(R.id.title);
        summary = findViewById(R.id.summary);
        communityRating = findViewById(R.id.community_rating);
        criticRating = findViewById(R.id.critic_rating);
        year = findViewById(R.id.year);
        duration = findViewById(R.id.duration);
        resumeProgress = findViewById(R.id.resumeProgress);
        watchedIndicator = findViewById(R.id.watchedIndicator);
        unwatchedCount = findViewById(R.id.unwatchedCount);
        checkMark = findViewById(R.id.checkMark);

        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.animation.StateListAnimator stateListAnimator = new android.animation.StateListAnimator();

            // default state with no animation
            stateListAnimator.addState(new int[0], android.animation.AnimatorInflater.loadAnimator(context, android.R.animator.fade_in));
            setStateListAnimator(stateListAnimator);
            // Explicitly set elevation to 0
            setElevation(0);
            setTranslationZ(0);
            setOutlineProvider(null);
        }

        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        setClipToPadding(false);
        setClipChildren(false);
        setOnLongClickListener(v -> {
            Activity activity = ContextExtensionsKt.getActivity(getContext());
            if (activity == null) return false;
            if (!v.requestFocus()) return false;
            return activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        });

        setForeground(null);

        setOnFocusChangeListener((v, hasFocus) -> {
            Timber.d("Focus changed: %s", hasFocus);
            updateCardBorder();
        });
        setFocusable(true);
        setFocusableInTouchMode(true);

        setOnClickListener(v -> {
            setSelected(!isSelected());
            Timber.d("Clicked, selected: %s", isSelected());
            updateCardBorder();
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Apply default scale
        setScaleX(defaultScale);
        setScaleY(defaultScale);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0);
            setTranslationZ(0);
            setStateListAnimator(null);
        }
    }

    private boolean isFocusedState = false;

    @SuppressLint({"NewApi", "WrongConstant"})
    private void updateCardBorder() {
        boolean shouldShowBorder = isFocused() || isSelected();

        Timber.d("Card border state - focused: %s, selected: %s, showing border: %s",
                isFocused(), isSelected(), shouldShowBorder);

        if (isFocusedState == shouldShowBorder) return;
        isFocusedState = shouldShowBorder;

        if (shouldShowBorder) {
            Drawable border = ContextCompat.getDrawable(getContext(), R.drawable.card_focused_border);
            int padding = (int) getContext().getResources().getDimension(R.dimen.card_border_padding);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mainImage.setForeground(border);
            } else {
                mainImage.setPadding(padding, padding, padding, padding);
                mainImage.setBackground(border);
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mainImage.setForeground(null);
            } else {
                mainImage.setPadding(0, 0, 0, 0);
                mainImage.setBackground(null);
            }
        }
        mainImage.invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        // Apply scale based on focus
        if (gainFocus) {
            animate().scaleX(focusScale).scaleY(focusScale).setDuration(150).start();
        } else {
            animate().scaleX(defaultScale).scaleY(defaultScale).setDuration(150).start();
        }

        float currentElevation = getElevation();
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0);
            setTranslationZ(0);
        }

        updateCardBorder();
    }

    @Override
    public void setSelected(boolean selected) {
        boolean wasSelected = isSelected();
        super.setSelected(selected);
        if (wasSelected != selected) {
            updateCardBorder();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Clear any running animations
            clearAnimation();
            setElevation(0);
            setTranslationZ(0);
            setOutlineProvider(null);
            invalidateOutline();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        setScaleX(defaultScale);
        setScaleY(defaultScale);

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(null);
            invalidateOutline();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        setScaleX(1.0f);
        setScaleY(1.0f);
        super.onDetachedFromWindow();
    }

    @androidx.lifecycle.OnLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
    public void onResume() {
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
        return mainImage;
    }

    public void setPlayingIndicator(boolean playing) {
    }

    public void setMainImageDimensions(int width, int height) {
        setMainImageDimensions(width, height, ImageView.ScaleType.CENTER_CROP);
    }

    public void setMainImageDimensions(int width, int height, ImageView.ScaleType scaleType) {
        ViewGroup.LayoutParams lp = mainImage.getLayoutParams();
        lp.width = Math.round(width * getResources().getDisplayMetrics().density);
        lp.height = Math.round(height * getResources().getDisplayMetrics().density);
        mainImage.setLayoutParams(lp);
        mainImage.setScaleType(scaleType);
        if (mBanner != null) mBanner.setX(lp.width - BANNER_SIZE);
        ViewGroup.LayoutParams lp2 = resumeProgress.getLayoutParams();
        lp2.width = lp.width;
        resumeProgress.setLayoutParams(lp2);
    }

    // Methods to set card data
    public void setTitle(String titleText) {
        title.setText(titleText);
    }

    public void setSummary(String summaryText) {
        summary.setText(summaryText);
    }

    public void setCommunityRating(Float rating) {
        if (rating != null && rating > 0) {
            communityRating.setText(nf.format(rating));
            communityRating.setVisibility(VISIBLE);
        } else {
            communityRating.setVisibility(GONE);
        }
    }

    public void setCriticRating(Float rating) {
        if (rating != null && rating > 0) {
            criticRating.setText(nf.format(rating) + "%");
            criticRating.setVisibility(VISIBLE);
        } else {
            criticRating.setVisibility(GONE);
        }
    }

    public void setYear(Integer yearValue) {
        if (yearValue != null) {
            year.setText(yearValue.toString());
            year.setVisibility(VISIBLE);
        } else {
            year.setVisibility(GONE);
        }
    }

    public void setDuration(Long runtimeTicks) {
        if (runtimeTicks != null && runtimeTicks > 0) {
            // Convert ticks to minutes (1 tick = 100 nanoseconds)
            long totalMinutes = runtimeTicks / 600000000L;
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;

            String durationText;
            if (hours > 0) {
                durationText = hours + "h " + minutes + "m";
            } else {
                durationText = minutes + "m";
            }

            duration.setText(durationText);
            duration.setVisibility(VISIBLE);
        } else {
            duration.setVisibility(GONE);
        }
    }

    public void setResumeProgress(int progress) {
        if (progress > 0 && progress < 100) {
            resumeProgress.setProgress(progress);
            resumeProgress.setVisibility(VISIBLE);
        } else {
            resumeProgress.setVisibility(GONE);
        }
    }

    public void setWatchedIndicator(boolean watched, int unwatchedCountValue) {
        if (watched || unwatchedCountValue > 0) {
            watchedIndicator.setVisibility(VISIBLE);
            if (watched) {
                checkMark.setVisibility(VISIBLE);
                unwatchedCount.setVisibility(INVISIBLE);
            } else {
                checkMark.setVisibility(INVISIBLE);
                unwatchedCount.setVisibility(VISIBLE);
                unwatchedCount.setText(String.valueOf(unwatchedCountValue));
            }
        } else {
            watchedIndicator.setVisibility(GONE);
        }
    }
}
