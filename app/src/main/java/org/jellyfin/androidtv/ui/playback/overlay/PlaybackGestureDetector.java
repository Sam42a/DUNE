package org.jellyfin.androidtv.ui.playback.overlay;

import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class PlaybackGestureDetector {
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final AudioManager audioManager;
    private final PlaybackGestureListener listener;
    private float initialX;
    private float initialY;
    private static final int HORIZONTAL_SWIPE_THRESHOLD = 100;
    private static final int VERTICAL_SWIPE_THRESHOLD = 100;

    public interface PlaybackGestureListener {
        void onSeekForward(long milliseconds);
        void onSeekBackward(long milliseconds);
        void onVolumeChange(int volume);
        void onTogglePlayPause();
    }

    public PlaybackGestureDetector(Context context, PlaybackGestureListener listener) {
        this.listener = listener;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                listener.onTogglePlayPause();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();

                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    // Horizontal swipe
                    if (Math.abs(deltaX) > HORIZONTAL_SWIPE_THRESHOLD) {
                        if (deltaX > 0) {
                            listener.onSeekForward(10000); // 10 seconds
                        } else {
                            listener.onSeekBackward(10000);
                        }
                        return true;
                    }
                } else {
                    // Vertical swipe
                    if (Math.abs(deltaY) > VERTICAL_SWIPE_THRESHOLD) {
                        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int volumeStep = maxVolume / 10;

                        if (deltaY > 0) {
                            // Swipe down - decrease volume
                            int newVolume = Math.max(0, currentVolume - volumeStep);
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                            listener.onVolumeChange(newVolume);
                        } else {
                            // Swipe up - increase volume
                            int newVolume = Math.min(maxVolume, currentVolume + volumeStep);
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                            listener.onVolumeChange(newVolume);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                
                // Scale volume based on pinch gesture
                int newVolume = (int) (currentVolume * scaleFactor);
                newVolume = Math.max(0, Math.min(maxVolume, newVolume));
                
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                listener.onVolumeChange(newVolume);
                return true;
            }
        });
    }

    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return gestureDetector.onTouchEvent(event);
    }
} 