package org.jellyfin.androidtv.ui.playback.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.jellyfin.androidtv.R;
import org.jellyfin.sdk.model.api.ChapterInfo;

import java.util.List;

public class CustomSeekBar extends androidx.appcompat.widget.AppCompatSeekBar {
    private List<ChapterInfo> chapters;
    private Paint chapterPaint;
    private Paint thumbnailPaint;
    private Rect thumbnailRect;
    private boolean showThumbnails = true;
    private int chapterMarkerHeight = 8;
    private int chapterMarkerWidth = 2;

    public CustomSeekBar(Context context) {
        super(context);
        init();
    }

    public CustomSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        chapterPaint = new Paint();
        chapterPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        chapterPaint.setStyle(Paint.Style.FILL);

        thumbnailPaint = new Paint();
        thumbnailPaint.setColor(ContextCompat.getColor(getContext(), android.R.color.white));
        thumbnailPaint.setAlpha(128);
        thumbnailRect = new Rect();
    }

    public void setChapters(List<ChapterInfo> chapters) {
        this.chapters = chapters;
        invalidate();
    }

    public void setShowThumbnails(boolean show) {
        this.showThumbnails = show;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chapters != null && !chapters.isEmpty()) {
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            long duration = getMax();

            // Draw chapter markers
            for (ChapterInfo chapter : chapters) {
                if (chapter.getStartPositionTicks() > 0) {
                    float position = (float) chapter.getStartPositionTicks() / duration;
                    int x = (int) (position * width) + getPaddingLeft();

                    // Draw chapter marker
                    canvas.drawRect(
                        x - chapterMarkerWidth / 2,
                        height - chapterMarkerHeight,
                        x + chapterMarkerWidth / 2,
                        height,
                        chapterPaint
                    );
                }
            }
        }

        // Draw thumbnail preview if enabled
        if (showThumbnails && isPressed()) {
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            float position = (float) getProgress() / getMax();
            int x = (int) (position * width) + getPaddingLeft();

            // Draw thumbnail preview
            thumbnailRect.set(
                x - 40,
                height - 60,
                x + 40,
                height - 20
            );
            canvas.drawRect(thumbnailRect, thumbnailPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            invalidate();
        }
        return super.onTouchEvent(event);
    }
}
