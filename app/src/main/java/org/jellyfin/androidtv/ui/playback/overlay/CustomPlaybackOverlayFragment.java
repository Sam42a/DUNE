package org.jellyfin.androidtv.ui.playback.overlay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.ui.playback.PlaybackController;
import org.jellyfin.androidtv.ui.playback.VideoManager;
import org.jellyfin.sdk.model.api.ChapterInfo;

import java.util.List;

public class CustomPlaybackOverlayFragment extends Fragment implements PlaybackGestureDetector.PlaybackGestureListener {
    private PlaybackController playbackController;
    private VideoManager videoManager;
    private CustomSeekBar seekBar;
    private TextView currentTime;
    private TextView totalTime;
    private ImageButton playPauseButton;
    private ImageButton prevButton;
    private ImageButton nextButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private ImageButton subtitlesButton;
    private ImageButton audioButton;
    private ImageButton fullscreenButton;
    private PlaybackGestureDetector gestureDetector;
    private boolean isControlsVisible = true;
    private boolean isFadingEnabled = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gestureDetector = new PlaybackGestureDetector(requireContext(), this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.playback_controls, container, false);

        initializeViews(view);
        setupListeners();
        updatePlayPauseButton();

        return view;
    }

    private void initializeViews(View view) {
        seekBar = view.findViewById(R.id.seekBar);
        currentTime = view.findViewById(R.id.currentTime);
        totalTime = view.findViewById(R.id.totalTime);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        prevButton = view.findViewById(R.id.prevButton);
        nextButton = view.findViewById(R.id.nextButton);
        rewindButton = view.findViewById(R.id.rewindButton);
        forwardButton = view.findViewById(R.id.forwardButton);
        subtitlesButton = view.findViewById(R.id.subtitlesButton);
        audioButton = view.findViewById(R.id.audioButton);
        fullscreenButton = view.findViewById(R.id.fullscreenButton);
    }

    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        prevButton.setOnClickListener(v -> playbackController.prev());
        nextButton.setOnClickListener(v -> playbackController.next());
        rewindButton.setOnClickListener(v -> playbackController.rewind());
        forwardButton.setOnClickListener(v -> playbackController.fastForward());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                showControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                playbackController.seek(seekBar.getProgress());
                if (isFadingEnabled) {
                    hideControlsDelayed();
                }
            }
        });

        View rootView = getView();
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    public void setPlaybackController(PlaybackController controller) {
        this.playbackController = controller;
    }

    public void setVideoManager(VideoManager manager) {
        this.videoManager = manager;
    }

    public void updatePlayPauseButton() {
        if (playbackController != null) {
            playPauseButton.setImageResource(
                playbackController.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow
            );
        }
    }

    public void updateProgress(long position, long duration) {
        seekBar.setMax((int) duration);
        seekBar.setProgress((int) position);
        currentTime.setText(formatTime(position));
        totalTime.setText(formatTime(duration));
    }

    public void setChapters(List<ChapterInfo> chapters) {
        seekBar.setChapters(chapters);
    }

    public void showControls() {
        if (!isControlsVisible) {
            isControlsVisible = true;
            getView().setVisibility(View.VISIBLE);
            getView().animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        }
    }

    public void hideControls() {
        if (isControlsVisible) {
            isControlsVisible = false;
            getView().animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> getView().setVisibility(View.GONE))
                .start();
        }
    }

    public void hideControlsDelayed() {
        getView().postDelayed(this::hideControls, 3000);
    }

    public void setFadingEnabled(boolean enabled) {
        isFadingEnabled = enabled;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", minutes, seconds % 60);
        }
    }

    // PlaybackGestureListener implementation
    @Override
    public void onSeekForward(long milliseconds) {
        playbackController.fastForward();
    }

    @Override
    public void onSeekBackward(long milliseconds) {
        playbackController.rewind();
    }

    @Override
    public void onVolumeChange(int volume) {
        // Volume is handled by the gesture detector
    }

    @Override
    public void onTogglePlayPause() {
        togglePlayPause();
    }

    private void togglePlayPause() {
        if (playbackController != null) {
            if (playbackController.isPlaying()) {
                playbackController.pause();
            } else {
                playbackController.play(playbackController.getCurrentPosition());
            }
            updatePlayPauseButton();
        }
    }
}
