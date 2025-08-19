package org.jellyfin.androidtv.ui.playback;

import static org.koin.java.KoinJavaComponent.inject;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.data.compat.PlaybackException;
import org.jellyfin.androidtv.data.compat.StreamInfo;
import org.jellyfin.androidtv.data.compat.VideoOptions;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.UserSettingPreferences;
import org.jellyfin.androidtv.preference.constant.NextUpBehavior;
import org.jellyfin.androidtv.preference.constant.RefreshRateSwitchingBehavior;
import org.jellyfin.androidtv.preference.constant.ZoomMode;
import org.jellyfin.androidtv.ui.livetv.TvManager;
import org.jellyfin.androidtv.util.TimeUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.ReportingHelper;
import org.jellyfin.androidtv.util.apiclient.Response;
import org.jellyfin.androidtv.util.profile.DeviceProfileKt;
import org.jellyfin.androidtv.util.sdk.compat.JavaCompat;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.DeviceProfile;
import org.jellyfin.sdk.model.api.LocationType;
import org.jellyfin.sdk.model.api.MediaSourceInfo;
import org.jellyfin.sdk.model.api.MediaStream;
import org.jellyfin.sdk.model.api.MediaStreamType;
import org.jellyfin.sdk.model.api.PlayMethod;
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod;
import org.jellyfin.sdk.model.serializer.UUIDSerializerKt;
import org.koin.java.KoinJavaComponent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import kotlin.Lazy;
import timber.log.Timber;

import java.time.Duration;

public class PlaybackController implements PlaybackControllerNotifiable {
    // Frequency to report playback progress
    private final static long PROGRESS_REPORTING_INTERVAL = TimeUtils.secondsToMillis(3);
    // Frequency to report paused state
    private static final long PROGRESS_REPORTING_PAUSE_INTERVAL = TimeUtils.secondsToMillis(15);

    private Lazy<PlaybackManager> playbackManager = inject(PlaybackManager.class);
    private Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);
    private Lazy<VideoQueueManager> videoQueueManager = inject(VideoQueueManager.class);
    private Lazy<org.jellyfin.sdk.api.client.ApiClient> api = inject(org.jellyfin.sdk.api.client.ApiClient.class);
    private Lazy<DataRefreshService> dataRefreshService = inject(DataRefreshService.class);
    private Lazy<ReportingHelper> reportingHelper = inject(ReportingHelper.class);

    List<BaseItemDto> mItems;
    VideoManager mVideoManager;
    int mCurrentIndex;
    protected long mCurrentPosition = 0;
    private PlaybackState mPlaybackState = PlaybackState.IDLE;

    private StreamInfo mCurrentStreamInfo;

    @Nullable
    private CustomPlaybackOverlayFragment mFragment;
    private Boolean spinnerOff = false;

    protected VideoOptions mCurrentOptions;
    private int mDefaultAudioIndex = -1;
    protected boolean burningSubs = false;
    private float mRequestedPlaybackSpeed = -1.0f;

    private Runnable mReportLoop;
    private Handler mHandler;

    private long mStartPosition = 0;

    // tmp position used when seeking
    private long mSeekPosition = -1;
    private boolean wasSeeking = false;
    private boolean finishedInitialSeek = false;

    private LocalDateTime mCurrentProgramEnd = null;
    private LocalDateTime mCurrentProgramStart = null;
    private long mCurrentTranscodeStartTime;
    private boolean isLiveTv = false;
    private boolean directStreamLiveTv;
    private int playbackRetries = 0;
    private long lastPlaybackError = 0;

    private Display.Mode[] mDisplayModes;
    private RefreshRateSwitchingBehavior refreshRateSwitchingBehavior = RefreshRateSwitchingBehavior.DISABLED;

    public PlaybackController(List<BaseItemDto> items, CustomPlaybackOverlayFragment fragment) {
        this(items, fragment, 0);
    }

    public PlaybackController(List<BaseItemDto> items, CustomPlaybackOverlayFragment fragment, int startIndex) {
        mItems = items;
        mCurrentIndex = 0;
        if (items != null && startIndex > 0 && startIndex < items.size()) {
            mCurrentIndex = startIndex;
        }
        mFragment = fragment;
        mHandler = new Handler();

        refreshRateSwitchingBehavior = userPreferences.getValue().get(UserPreferences.Companion.getRefreshRateSwitchingBehavior());
        if (refreshRateSwitchingBehavior != RefreshRateSwitchingBehavior.DISABLED)
            getDisplayModes();
    }

    public boolean hasFragment() {
        return mFragment != null;
    }

    public CustomPlaybackOverlayFragment getFragment() {
        return mFragment;
    }

    public void init(@NonNull VideoManager mgr, @NonNull CustomPlaybackOverlayFragment fragment) {
        mVideoManager = mgr;
        mVideoManager.subscribe(this);
        mVideoManager.setZoom(userPreferences.getValue().get(UserPreferences.Companion.getPlayerZoomMode()));
        mFragment = fragment;
        directStreamLiveTv = userPreferences.getValue().get(UserPreferences.Companion.getLiveTvDirectPlayEnabled());
    }

    public void setItems(List<BaseItemDto> items) {
        mItems = items;
        mCurrentIndex = 0;
    }

    public float getPlaybackSpeed() {
        if (hasInitializedVideoManager()) {
            return mVideoManager.getPlaybackSpeed();
        } else {
            return mRequestedPlaybackSpeed;
        }
    }

    public void setPlaybackSpeed(float speed) {
        mRequestedPlaybackSpeed = speed;
        if (hasInitializedVideoManager()) {
            mVideoManager.setPlaybackSpeed(speed);
        }
    }

    public BaseItemDto getCurrentlyPlayingItem() {
        return mItems.size() > mCurrentIndex ? mItems.get(mCurrentIndex) : null;
    }

    public boolean hasInitializedVideoManager() {
        return mVideoManager != null && mVideoManager.isInitialized();
    }

    public org.jellyfin.sdk.model.api.MediaSourceInfo getCurrentMediaSource() {
        if (mCurrentStreamInfo != null && mCurrentStreamInfo.getMediaSource() != null) {
            return mCurrentStreamInfo.getMediaSource();
        } else {
            BaseItemDto item = getCurrentlyPlayingItem();
            List<org.jellyfin.sdk.model.api.MediaSourceInfo> mediaSources = item.getMediaSources();

            if (mediaSources == null || mediaSources.isEmpty()) {
                return null;
            } else {
                for (MediaSourceInfo mediaSource : mediaSources) {
                    if (UUIDSerializerKt.toUUIDOrNull(mediaSource.getId()).equals(item.getId())) {
                        return mediaSource;
                    }
                }
                return mediaSources.get(0);
            }
        }
    }

    public StreamInfo getCurrentStreamInfo() {
        return mCurrentStreamInfo;
    }

    public boolean canSeek() {
        return !isLiveTv;
    }

    public boolean isLiveTv() {
        return isLiveTv;
    }

    public int getSubtitleStreamIndex() {
        return (mCurrentOptions != null && mCurrentOptions.getSubtitleStreamIndex() != null) ? mCurrentOptions.getSubtitleStreamIndex() : -1;
    }

    public boolean isTranscoding() {
        return mCurrentStreamInfo == null || mCurrentStreamInfo.getPlayMethod() == PlayMethod.TRANSCODE;
    }

    public boolean hasNextItem() {
        return mItems != null && mCurrentIndex < mItems.size() - 1;
    }

    public BaseItemDto getNextItem() {
        return hasNextItem() ? mItems.get(mCurrentIndex + 1) : null;
    }

    public boolean hasPreviousItem() {
        return mItems != null && mCurrentIndex - 1 >= 0;
    }

    public boolean isPlaying() {
        return mPlaybackState == PlaybackState.PLAYING && hasInitializedVideoManager() && mVideoManager.isPlaying();
    }

    public void playerErrorEncountered() {
        if (playbackRetries > 0 && Instant.now().toEpochMilli() - lastPlaybackError > 30000) {
            Timber.d("playback stabilized - retry count reset to 0 from %s", playbackRetries);
            playbackRetries = 0;
        }

        playbackRetries++;
        lastPlaybackError = Instant.now().toEpochMilli();

        if (playbackRetries < 3) {
            if (mFragment != null)
                Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.player_error));
            Timber.i("Player error encountered - retrying");
            stop();
            play(mCurrentPosition);
        } else {
            mPlaybackState = PlaybackState.ERROR;
            if (mFragment != null) {
                Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.too_many_errors));
                mFragment.closePlayer();
            }
        }
    }

    @TargetApi(23)
    private void getDisplayModes() {
        if (mFragment == null)
            return;
        Display display = mFragment.requireActivity().getWindowManager().getDefaultDisplay();
        mDisplayModes = display.getSupportedModes();
        Timber.i("** Available display refresh rates:");
        for (Display.Mode mDisplayMode : mDisplayModes) {
            Timber.d("display mode %s - %dx%d@%f", mDisplayMode.getModeId(), mDisplayMode.getPhysicalWidth(), mDisplayMode.getPhysicalHeight(), mDisplayMode.getRefreshRate());
        }
    }

    @TargetApi(23)
    private Display.Mode findBestDisplayMode(MediaStream videoStream) {
        if (mFragment == null || mDisplayModes == null || videoStream.getRealFrameRate() == null)
            return null;

        int curWeight = 0;
        Display.Mode bestMode = null;
        int sourceRate = Math.round(videoStream.getRealFrameRate() * 100);

        Display.Mode defaultMode = mFragment.requireActivity().getWindowManager().getDefaultDisplay().getMode();

        Timber.d("trying to find display mode for video: %dx%d@%f", videoStream.getWidth(), videoStream.getHeight(), videoStream.getRealFrameRate());
        for (Display.Mode mode : mDisplayModes) {
            Timber.d("considering display mode: %s - %dx%d@%f", mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());

            if (mode.getPhysicalWidth() < 1280 || mode.getPhysicalHeight() < 720)
                continue;

            if (mode.getPhysicalWidth() < videoStream.getWidth() || mode.getPhysicalHeight() < videoStream.getHeight())
                continue;

            int rate = Math.round(mode.getRefreshRate() * 100);
            if (rate != sourceRate && rate != sourceRate * 2 && rate != Math.round(sourceRate * 2.5))
                continue;

            Timber.d("qualifying display mode: %s - %dx%d@%f", mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());

            int resolutionDifference = -1;
            if ((refreshRateSwitchingBehavior == RefreshRateSwitchingBehavior.SCALE_ON_DEVICE &&
                    !(mode.getPhysicalWidth() == defaultMode.getPhysicalWidth() && mode.getPhysicalHeight() == defaultMode.getPhysicalHeight())) ||
                    refreshRateSwitchingBehavior == RefreshRateSwitchingBehavior.SCALE_ON_TV) {
                resolutionDifference = Math.abs(mode.getPhysicalWidth() - videoStream.getWidth());
            }
            int refreshRateDifference = rate - sourceRate;

            int weight = 100000 - refreshRateDifference + 100000 - resolutionDifference;

            if (weight > curWeight) {
                Timber.d("preferring mode: %s - %dx%d@%f", mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
                curWeight = weight;
                bestMode = mode;
            }
        }

        return bestMode;
    }

    @TargetApi(23)
    private void setRefreshRate(org.jellyfin.sdk.model.api.MediaStream videoStream) {
        if (videoStream == null || mFragment == null) {
            Timber.e("Null video stream attempting to set refresh rate");
            return;
        }

        Display.Mode current = mFragment.requireActivity().getWindowManager().getDefaultDisplay().getMode();
        Display.Mode best = findBestDisplayMode(videoStream);
        if (best != null) {
            Timber.i("*** Best refresh mode is: %s - %dx%d/%f",
                    best.getModeId(), best.getPhysicalWidth(), best.getPhysicalHeight(), best.getRefreshRate());
            if (current.getModeId() != best.getModeId()) {
                Timber.i("*** Attempting to change refresh rate from: %s - %dx%d@%f", current.getModeId(), current.getPhysicalWidth(),
                        current.getPhysicalHeight(), current.getRefreshRate());
                WindowManager.LayoutParams params = mFragment.requireActivity().getWindow().getAttributes();
                params.preferredDisplayModeId = best.getModeId();
                mFragment.requireActivity().getWindow().setAttributes(params);
            } else {
                Timber.i("Display is already in best mode");
            }
        } else {
            Timber.i("*** Unable to find display mode for refresh rate: %f", videoStream.getRealFrameRate());
        }
    }

    private void refreshCurrentPosition() {
        long newPos = -1;

        if (isLiveTv && mCurrentProgramStart != null) {
            newPos = getRealTimeProgress();
        } else if (hasInitializedVideoManager()) {
            if (currentSkipPos != 0 || (!isPlaying() && mSeekPosition != -1)) {
                newPos = mSeekPosition;
            } else if (isPlaying()) {
                if (finishedInitialSeek) {
                    newPos = mVideoManager.getCurrentPosition();
                    mSeekPosition = -1;
                } else if (wasSeeking) {
                    finishedInitialSeek = true;
                } else if (mSeekPosition != -1) {
                    newPos = mSeekPosition;
                }
                wasSeeking = false;
            }
        }
        mCurrentPosition = newPos != -1 ? newPos : mCurrentPosition;
    }

    public void play(long position) {
        play(position, null);
    }

    protected void play(long position, @Nullable Integer forcedSubtitleIndex) {
        String forcedAudioLanguage = videoQueueManager.getValue().getLastPlayedAudioLanguageIsoCode();
        Timber.i("Play called from state: %s with pos: %d, sub index: %d and forced audio: %s", mPlaybackState, position, forcedSubtitleIndex, forcedAudioLanguage);

        if (mFragment == null) {
            Timber.w("mFragment is null, returning");
            return;
        }

        if (position < 0) {
            Timber.i("Negative start requested - adjusting to zero");
            position = 0;
        }

        switch (mPlaybackState) {
            case PLAYING:
                break;
            case PAUSED:
                if (!hasInitializedVideoManager()) {
                    return;
                }
                mVideoManager.play();
                mPlaybackState = PlaybackState.PLAYING;
                mFragment.setFadingEnabled(true);
                startReportLoop();
                break;
            case BUFFERING:
                break;
            case IDLE:
                mSeekPosition = position;
                mCurrentPosition = 0;

                mFragment.setFadingEnabled(false);

                BaseItemDto item = getCurrentlyPlayingItem();

                if (item == null) {
                    Timber.d("item is null - aborting play");
                    Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_cannot_play));
                    mFragment.closePlayer();
                    return;
                }

                if (item.getLocationType() == LocationType.VIRTUAL) {
                    if (hasNextItem()) {
                        new AlertDialog.Builder(mFragment.getContext())
                                .setTitle(R.string.episode_missing)
                                .setMessage(R.string.episode_missing_message)
                                .setPositiveButton(R.string.lbl_yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        next();
                                    }
                                })
                                .setNegativeButton(R.string.lbl_no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mFragment.closePlayer();
                                    }
                                })
                                .create()
                                .show();
                    } else {
                        new AlertDialog.Builder(mFragment.getContext())
                                .setTitle(R.string.episode_missing)
                                .setMessage(R.string.episode_missing_message_2)
                                .setPositiveButton(R.string.lbl_ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mFragment.closePlayer();
                                    }
                                })
                                .create()
                                .show();
                    }
                    return;
                }

                isLiveTv = item.getType() == BaseItemKind.TV_CHANNEL;
                startSpinner();

                if (isLiveTv) mSeekPosition = -1;

                VideoOptions internalOptions = buildExoPlayerOptions(forcedSubtitleIndex, forcedAudioLanguage, item);

                playInternal(getCurrentlyPlayingItem(), position, internalOptions);
                mPlaybackState = PlaybackState.BUFFERING;
                mFragment.setPlayPauseActionState(0);
                mFragment.setCurrentTime(position);

                long duration = getCurrentlyPlayingItem().getRunTimeTicks() != null ? getCurrentlyPlayingItem().getRunTimeTicks() / 10000 : -1;
                if (mVideoManager != null)
                    mVideoManager.setMetaDuration(duration);

                break;
        }
    }

    @NonNull
    private VideoOptions buildExoPlayerOptions(
            @Nullable Integer forcedSubtitleIndex,
            @Nullable String forcedAudioLanguage,
            BaseItemDto item
    ) {
        VideoOptions internalOptions = new VideoOptions();
        internalOptions.setItemId(item.getId());
        internalOptions.setMediaSources(item.getMediaSources());
        if (playbackRetries > 0 || (isLiveTv && !directStreamLiveTv)) internalOptions.setEnableDirectStream(false);
        if (playbackRetries > 1) internalOptions.setEnableDirectPlay(false);
        if (mCurrentOptions != null) {
            internalOptions.setSubtitleStreamIndex(mCurrentOptions.getSubtitleStreamIndex());
            internalOptions.setAudioStreamIndex(mCurrentOptions.getAudioStreamIndex());
        }
        if (forcedSubtitleIndex != null) {
            internalOptions.setSubtitleStreamIndex(forcedSubtitleIndex);
        }
        MediaSourceInfo currentMediaSource = getCurrentMediaSource();
        if (!isLiveTv && currentMediaSource != null) {
            internalOptions.setMediaSourceId(currentMediaSource.getId());
        }

        String preferredLanguage = userPreferences.getValue().get(UserPreferences.Companion.getDefaultAudioLanguage());
        Timber.d("buildExoPlayerOptions: Preferred audio language from settings: %s, forced audio language: %s", preferredLanguage, forcedAudioLanguage);

        if (currentMediaSource != null && currentMediaSource.getMediaStreams() != null) {
            if (!TextUtils.isEmpty(preferredLanguage)) {
                for (MediaStream stream : currentMediaSource.getMediaStreams()) {
                    Timber.d("buildExoPlayerOptions: Checking stream index: %d, type: %s, language: %s, codec: %s",
                            stream.getIndex(), stream.getType(), stream.getLanguage(), stream.getCodec());
                    if (stream.getType() == MediaStreamType.AUDIO &&
                            stream.getLanguage() != null &&
                            stream.getLanguage().equalsIgnoreCase(preferredLanguage)) {
                        internalOptions.setAudioStreamIndex(stream.getIndex());
                        Timber.i("buildExoPlayerOptions: Selected audio track index %d for preferred language: %s",
                                stream.getIndex(), preferredLanguage);
                        videoQueueManager.getValue().setLastPlayedAudioLanguageIsoCode(stream.getLanguage());
                        break;
                    }
                }
            }

            if (internalOptions.getAudioStreamIndex() == null && !TextUtils.isEmpty(forcedAudioLanguage)) {
                for (MediaStream stream : currentMediaSource.getMediaStreams()) {
                    Timber.d("buildExoPlayerOptions: Checking stream index: %d, type: %s, language: %s, codec: %s",
                            stream.getIndex(), stream.getType(), stream.getLanguage(), stream.getCodec());
                    if (stream.getType() == MediaStreamType.AUDIO &&
                            stream.getLanguage() != null &&
                            stream.getLanguage().equalsIgnoreCase(forcedAudioLanguage)) {
                        internalOptions.setAudioStreamIndex(stream.getIndex());
                        Timber.i("buildExoPlayerOptions: Selected audio track index %d for forced language: %s",
                                stream.getIndex(), forcedAudioLanguage);
                        videoQueueManager.getValue().setLastPlayedAudioLanguageIsoCode(stream.getLanguage());
                        break;
                    }
                }
            }

            if (internalOptions.getAudioStreamIndex() == null) {
                for (MediaStream stream : currentMediaSource.getMediaStreams()) {
                    if (stream.getType() == MediaStreamType.AUDIO) {
                        internalOptions.setAudioStreamIndex(stream.getIndex());
                        Timber.w("buildExoPlayerOptions: No preferred or forced language matched, falling back to first audio track index: %d, language: %s",
                                stream.getIndex(), stream.getLanguage());
                        if (stream.getLanguage() != null) {
                            videoQueueManager.getValue().setLastPlayedAudioLanguageIsoCode(stream.getLanguage());
                        }
                        break;
                    }
                }
            }
        } else {
            Timber.w("buildExoPlayerOptions: No media streams available for audio selection");
        }

        if (!isLiveTv && currentMediaSource != null) {
            internalOptions.setMediaSourceId(currentMediaSource.getId());
        }
        DeviceProfile internalProfile = DeviceProfileKt.createDeviceProfile(
                mFragment.getContext(),
                userPreferences.getValue(),
                !internalOptions.getEnableDirectStream()
        );
        internalOptions.setProfile(internalProfile);
        return internalOptions;
    }

    private void playInternal(final BaseItemDto item, final Long position, final VideoOptions internalOptions) {
        if (isLiveTv) {
            updateTvProgramInfo();
            TvManager.setLastLiveTvChannel(item.getId());
            Timber.i("Using internal player for Live TV");
            playbackManager.getValue().getVideoStreamInfo(mFragment, internalOptions, position * 10000, new Response<StreamInfo>() {
                @Override
                public void onResponse(StreamInfo response) {
                    if (mVideoManager == null)
                        return;
                    mCurrentOptions = internalOptions;
                    startItem(item, position, response);
                }

                @Override
                public void onError(Exception exception) {
                    handlePlaybackInfoError(exception);
                }
            });
        } else {
            playbackManager.getValue().getVideoStreamInfo(mFragment, internalOptions, position * 10000, new Response<StreamInfo>() {
                @Override
                public void onResponse(StreamInfo internalResponse) {
                    Timber.i("Internal player would %s", internalResponse.getPlayMethod().equals(PlayMethod.TRANSCODE) ? "transcode" : "direct stream");
                    if (mVideoManager == null)
                        return;
                    mCurrentOptions = internalOptions;
                    if (internalOptions.getSubtitleStreamIndex() == null) burningSubs = internalResponse.getSubtitleDeliveryMethod() == SubtitleDeliveryMethod.ENCODE;
                    startItem(item, position, internalResponse);
                }

                @Override
                public void onError(Exception exception) {
                    Timber.e(exception, "Unable to get stream info for internal player");
                    if (mVideoManager == null)
                        return;
                }
            });
        }
    }

    private void handlePlaybackInfoError(Exception exception) {
        Timber.e(exception, "Error getting playback stream info");
        if (mFragment == null) return;
        if (exception instanceof PlaybackException) {
            PlaybackException ex = (PlaybackException) exception;
            switch (ex.getErrorCode()) {
                case NOT_ALLOWED:
                    Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_playback_not_allowed));
                    break;
                case NO_COMPATIBLE_STREAM:
                    Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_playback_incompatible));
                    break;
                case RATE_LIMIT_EXCEEDED:
                    Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_playback_restricted));
                    break;
            }
        } else {
            Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_cannot_play));
        }
        if (mFragment != null) mFragment.closePlayer();
    }

    private int mLastIndex = -1;

    private void startItem(BaseItemDto item, long position, StreamInfo response) {
        if (mVideoManager == null) {
            Timber.w("Video manager is null, can't start item");
            return;
        }

        if (mCurrentIndex != mLastIndex) {
            clearPlaybackSessionOptions();
            mCurrentOptions.setAudioStreamIndex(null);
            mLastIndex = mCurrentIndex;
        }

        if (!hasInitializedVideoManager() || !hasFragment()) {
            Timber.d("Error - attempting to play without:%s%s", hasInitializedVideoManager() ? "" : " [videoManager]", hasFragment() ? "" : " [overlay fragment]");
            return;
        }

        mStartPosition = position;
        mCurrentStreamInfo = response;
        mCurrentOptions.setMediaSourceId(response.getMediaSource().getId());

        if (response.getMediaUrl() == null) {
            if (response.getSubtitleDeliveryMethod() == SubtitleDeliveryMethod.ENCODE && (response.getMediaSource().getDefaultSubtitleStreamIndex() == null || response.getMediaSource().getDefaultSubtitleStreamIndex() != -1)) {
                burningSubs = false;
                stop();
                play(position, -1);
            } else {
                handlePlaybackInfoError(null);
            }
            return;
        }

        mCurrentOptions.setSubtitleStreamIndex(response.getMediaSource().getDefaultSubtitleStreamIndex() != null ? response.getMediaSource().getDefaultSubtitleStreamIndex() : null);
        setDefaultAudioIndex(response);

        if (mCurrentOptions.getAudioStreamIndex() != null) {
            MediaSourceInfo mediaSource = getCurrentMediaSource();
            if (mediaSource != null) {
                for (MediaStream stream : mediaSource.getMediaStreams()) {
                    if (stream.getType() == MediaStreamType.AUDIO && stream.getIndex() == mCurrentOptions.getAudioStreamIndex()) {
                        if (stream.getLanguage() != null) {
                            videoQueueManager.getValue().setLastPlayedAudioLanguageIsoCode(stream.getLanguage());
                            Timber.i("startItem: Persisted audio language: %s for index: %d", stream.getLanguage(), mCurrentOptions.getAudioStreamIndex());
                        }
                        break;
                    }
                }
            }
        }
        Timber.d("default audio index set to %s remote default %s", mDefaultAudioIndex, response.getMediaSource().getDefaultAudioStreamIndex());
        Timber.d("default sub index set to %s remote default %s", mCurrentOptions.getSubtitleStreamIndex(), response.getMediaSource().getDefaultSubtitleStreamIndex());

        Long mbPos = position * 10000;

        if (refreshRateSwitchingBehavior != RefreshRateSwitchingBehavior.DISABLED) {
            setRefreshRate(JavaCompat.getVideoStream(response.getMediaSource()));
        }

        if (mVideoManager != null)
            mVideoManager.setPlaybackSpeed(isLiveTv() ? 1.0f : mRequestedPlaybackSpeed);

        if (mFragment != null) mFragment.updateDisplay();

        if (mVideoManager != null) {
            mVideoManager.setMediaStreamInfo(api.getValue(), response);
        }

        PlaybackControllerHelperKt.applyMediaSegments(this, item, () -> {
            long videoStartDelay = userPreferences.getValue().get(UserPreferences.Companion.getVideoStartDelay());
            if (videoStartDelay > 0) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mVideoManager != null) {
                            mVideoManager.start();
                        }
                    }
                }, videoStartDelay);
            } else {
                mVideoManager.start();
            }

            dataRefreshService.getValue().setLastPlayedItem(item);
            reportingHelper.getValue().reportStart(mFragment, PlaybackController.this, item, response, mbPos, false);

            return null;
        });
    }

    public void startSpinner() {
        spinnerOff = false;
    }

    public void stopSpinner() {
        spinnerOff = true;
    }

    public int getAudioStreamIndex() {
        // First, check the actual ExoPlayer track to reflect the true playback state
        if (hasInitializedVideoManager() && !isTranscoding()) {
            Integer track = mVideoManager.getExoPlayerTrack(MediaStreamType.AUDIO, getCurrentlyPlayingItem().getMediaStreams());
            if (track != null) {
                Timber.d("getAudioStreamIndex: Returning actual ExoPlayer audio track index %d", track);
                return track;
            }
        }

        // Fall back to mCurrentOptions if ExoPlayer track is unavailable
        if (mCurrentOptions != null && mCurrentOptions.getAudioStreamIndex() != null) {
            Timber.d("getAudioStreamIndex: Returning audio index %d from mCurrentOptions", mCurrentOptions.getAudioStreamIndex());
            return mCurrentOptions.getAudioStreamIndex();
        }

        // Try to find a preferred audio track
        if (getCurrentMediaSource() != null && getCurrentMediaSource().getMediaStreams() != null) {
            Integer preferredTrack = bestGuessAudioTrack(getCurrentMediaSource());
            if (preferredTrack != null) {
                Timber.d("getAudioStreamIndex: Returning preferred audio track index %d", preferredTrack);
                return preferredTrack;
            }
        }

        // Use server default if available
        if (isTranscoding() && getCurrentMediaSource() != null && getCurrentMediaSource().getDefaultAudioStreamIndex() != null) {
            Timber.d("getAudioStreamIndex: Returning server default audio index %d", getCurrentMediaSource().getDefaultAudioStreamIndex());
            return getCurrentMediaSource().getDefaultAudioStreamIndex();
        }

        // Fallback for live TV
        if (isLiveTv() && hasInitializedVideoManager()) {
            Integer track = mVideoManager.getExoPlayerTrack(MediaStreamType.AUDIO, getCurrentlyPlayingItem().getMediaStreams());
            if (track != null) {
                Timber.d("getAudioStreamIndex: Returning live TV audio track index %d from VideoManager", track);
                return track;
            }
        }

        Timber.w("getAudioStreamIndex: No audio track found, returning -1");
        return -1;
    }

    private Integer bestGuessAudioTrack(MediaSourceInfo info) {
        if (info == null || info.getMediaStreams() == null) {
            Timber.w("bestGuessAudioTrack: MediaSourceInfo or media streams are null");
            return null;
        }

        Timber.d("bestGuessAudioTrack: Checking for preferred audio track");
        String preferredLanguage = userPreferences.getValue().get(UserPreferences.Companion.getDefaultAudioLanguage());
        Timber.d("bestGuessAudioTrack: Preferred language from settings: %s", preferredLanguage);

        if (!TextUtils.isEmpty(preferredLanguage)) {
            for (MediaStream track : info.getMediaStreams()) {
                Timber.d("bestGuessAudioTrack: Checking track %d - type: %s, language: %s, codec: %s",
                        track.getIndex(), track.getType(), track.getLanguage(), track.getCodec());
                if (track.getType() == MediaStreamType.AUDIO &&
                        track.getLanguage() != null &&
                        track.getLanguage().equalsIgnoreCase(preferredLanguage)) {
                    Timber.i("bestGuessAudioTrack: Found matching track at index %d for language %s", track.getIndex(), preferredLanguage);
                    return track.getIndex();
                }
            }
            Timber.d("bestGuessAudioTrack: No tracks found matching preferred language %s", preferredLanguage);
        } else {
            Timber.d("bestGuessAudioTrack: No preferred language set in settings");
        }

        Timber.d("bestGuessAudioTrack: Falling back to first audio track after video");
        boolean videoFound = false;
        for (MediaStream track : info.getMediaStreams()) {
            if (track.getType() == MediaStreamType.VIDEO) {
                videoFound = true;
                Timber.d("bestGuessAudioTrack: Found video track, now looking for audio");
            } else if (videoFound && track.getType() == MediaStreamType.AUDIO) {
                Timber.i("bestGuessAudioTrack: Found first audio track after video at index %d (language: %s)",
                        track.getIndex(), track.getLanguage());
                return track.getIndex();
            }
        }

        for (MediaStream track : info.getMediaStreams()) {
            if (track.getType() == MediaStreamType.AUDIO) {
                Timber.i("bestGuessAudioTrack: Fallback to first audio track at index %d (language: %s)",
                        track.getIndex(), track.getLanguage());
                return track.getIndex();
            }
        }

        Timber.w("bestGuessAudioTrack: No audio tracks found");
        return null;
    }

    private void setDefaultAudioIndex(StreamInfo info) {
        if (mDefaultAudioIndex != -1) {
            Timber.d("setDefaultAudioIndex: Default audio index already set to %d, skipping", mDefaultAudioIndex);
            return;
        }

        Integer remoteDefault = info.getMediaSource().getDefaultAudioStreamIndex();
        Integer bestGuess = bestGuessAudioTrack(info.getMediaSource());

        if (mCurrentOptions.getAudioStreamIndex() != null) {
            mDefaultAudioIndex = mCurrentOptions.getAudioStreamIndex();
            Timber.i("setDefaultAudioIndex: Using audio index %d from mCurrentOptions", mDefaultAudioIndex);
        } else if (remoteDefault != null) {
            mDefaultAudioIndex = remoteDefault;
            Timber.i("setDefaultAudioIndex: Using remote default audio index %d", mDefaultAudioIndex);
        } else if (bestGuess != null) {
            mDefaultAudioIndex = bestGuess;
            Timber.i("setDefaultAudioIndex: Using best guess audio index %d", mDefaultAudioIndex);
        } else {
            Timber.w("setDefaultAudioIndex: No audio index selected, defaulting to -1");
            mDefaultAudioIndex = -1;
        }
    }

    public void switchAudioStream(int index) {
        if (!(isPlaying() || isPaused()) || index < 0)
            return;

        int currAudioIndex = getAudioStreamIndex();
        Timber.d("trying to switch audio stream from %s to %s", currAudioIndex, index);
        if (currAudioIndex == index) {
            Timber.d("skipping setting audio stream, already set to requested index %s", index);
            if (mCurrentOptions.getAudioStreamIndex() == null || mCurrentOptions.getAudioStreamIndex() != index) {
                Timber.d("setting mCurrentOptions audio stream index from %s to %s", mCurrentOptions.getAudioStreamIndex(), index);
                mCurrentOptions.setAudioStreamIndex(index);
            }
            return;
        }

        // get current timestamp first
        refreshCurrentPosition();

        if (!isTranscoding() && mVideoManager.setExoPlayerTrack(index, MediaStreamType.AUDIO, getCurrentlyPlayingItem().getMediaStreams())) {
            mCurrentOptions.setMediaSourceId(getCurrentMediaSource().getId());
            mCurrentOptions.setAudioStreamIndex(index);
        } else {
            startSpinner();
            mCurrentOptions.setMediaSourceId(getCurrentMediaSource().getId());
            mCurrentOptions.setAudioStreamIndex(index);
            stop();
            playInternal(getCurrentlyPlayingItem(), mCurrentPosition, mCurrentOptions);
            mPlaybackState = PlaybackState.BUFFERING;
        }
    }

    public void pause() {
        Timber.d("pause called at %s", mCurrentPosition);
        if (mPlaybackState == PlaybackState.PAUSED) {
            Timber.d("already paused, ignoring");
            return;
        }
        mPlaybackState = PlaybackState.PAUSED;
        if (hasInitializedVideoManager()) mVideoManager.pause();
        if (mFragment != null) {
            mFragment.setFadingEnabled(false);
            mFragment.setPlayPauseActionState(0);
        }

        stopReportLoop();
        startPauseReportLoop();
    }

    public void playPause() {
        switch (mPlaybackState) {
            case PLAYING:
                pause();
                break;
            case PAUSED:
            case IDLE:
                stopReportLoop();
                play(getCurrentPosition());
                break;
        }
    }

    public void stop() {
        refreshCurrentPosition();
        Timber.d("stop called at %s", mCurrentPosition);
        stopReportLoop();
        if (mPlaybackState != PlaybackState.IDLE && mPlaybackState != PlaybackState.UNDEFINED) {
            mPlaybackState = PlaybackState.IDLE;

            if (mVideoManager != null && mVideoManager.isPlaying()) mVideoManager.stopPlayback();
            if (getCurrentlyPlayingItem() != null && mCurrentStreamInfo != null) {
                Long mbPos = mCurrentPosition * 10000;
                reportingHelper.getValue().reportStopped(mFragment, getCurrentlyPlayingItem(), mCurrentStreamInfo, mbPos);
            }
            clearPlaybackSessionOptions();
        }
    }

    public void refreshStream() {
        refreshCurrentPosition();
        stop();
        play(mCurrentPosition);
    }

    public void endPlayback(Boolean closeActivity) {
        if (closeActivity && mFragment != null) mFragment.closePlayer();
        stop();
        if (mVideoManager != null)
            mVideoManager.destroy();
        mFragment = null;
        mVideoManager = null;
        resetPlayerErrors();
    }

    public void endPlayback() {
        endPlayback(false);
    }

    private void resetPlayerErrors() {
        playbackRetries = 0;
    }

    private void clearPlaybackSessionOptions() {
        mDefaultAudioIndex = -1;
        mSeekPosition = -1;
        finishedInitialSeek = false;
        wasSeeking = false;
        burningSubs = false;
        mCurrentStreamInfo = null;
    }

    public void next() {
        Timber.d("Next called.");
        if (mCurrentIndex < mItems.size() - 1) {
            stop();
            resetPlayerErrors();
            mCurrentIndex++;
            videoQueueManager.getValue().setCurrentMediaPosition(mCurrentIndex);
            Timber.d("Moving to index: %d out of %d total items.", mCurrentIndex, mItems.size());
            spinnerOff = false;
            play(0);
        }
    }

    public void prev() {
        Timber.d("Prev called.");
        if (mCurrentIndex > 0 && mItems.size() > 0) {
            stop();
            resetPlayerErrors();
            mCurrentIndex--;
            videoQueueManager.getValue().setCurrentMediaPosition(mCurrentIndex);
            Timber.d("Moving to index: %d out of %d total items.", mCurrentIndex, mItems.size());
            spinnerOff = false;
            play(0);
        }
    }

    public void fastForward() {
        UserSettingPreferences prefs = KoinJavaComponent.<UserSettingPreferences>get(UserSettingPreferences.class);
        skip(prefs.get(prefs.skipForwardLength));
    }

    public void rewind() {
        UserSettingPreferences prefs = KoinJavaComponent.<UserSettingPreferences>get(UserSettingPreferences.class);
        skip(-prefs.get(prefs.skipBackLength));
    }

    public void seek(long pos) {
        seek(pos, false);
    }

    public void seek(long pos, boolean skipToNext) {
        if (pos <= 0) pos = 0;

        Timber.d("Trying to seek from %s to %d", mCurrentPosition, pos);
        Timber.d("Container: %s", mCurrentStreamInfo == null ? "unknown" : mCurrentStreamInfo.getContainer());

        if (!hasInitializedVideoManager()) {
            return;
        }

        if (wasSeeking) {
            Timber.d("Previous seek has not finished - cancelling seek from %s to %d", mCurrentPosition, pos);
            if (isPaused()) {
                refreshCurrentPosition();
                play(mCurrentPosition);
            }
            return;
        }
        wasSeeking = true;

        if (skipToNext && pos >= (getDuration() - 100)) {
            mCurrentPosition = getDuration();
            currentSkipPos = mCurrentPosition;
            mSeekPosition = mCurrentPosition;
            itemComplete();
            return;
        }

        if (pos >= getDuration()) pos = getDuration();

        mSeekPosition = pos;

        if (mCurrentStreamInfo == null) return;

        if (!mVideoManager.isSeekable()) {
            Timber.d("Seek method - rebuilding the stream");
            mVideoManager.stopPlayback();
            mPlaybackState = PlaybackState.BUFFERING;

            playbackManager.getValue().changeVideoStream(mFragment, mCurrentStreamInfo, mCurrentOptions, pos * 10000, new Response<StreamInfo>() {
                @Override
                public void onResponse(StreamInfo response) {
                    mCurrentStreamInfo = response;
                    if (mVideoManager != null) {
                        mVideoManager.setMediaStreamInfo(api.getValue(), response);
                        mVideoManager.start();
                        mPlaybackState = PlaybackState.PLAYING;
                        if (mFragment != null) {
                            mFragment.setFadingEnabled(true);
                            mFragment.setPlayPauseActionState(1);
                        }
                        startReportLoop();
                    }
                }

                @Override
                public void onError(Exception exception) {
                    if (mFragment != null)
                        Utils.showToast(mFragment.getContext(), R.string.msg_video_playback_error);
                    Timber.e(exception, "Error trying to seek transcoded stream");
                    stop();
                }
            });
        } else {
            mPlaybackState = PlaybackState.SEEKING;
            if (mVideoManager.seekTo(pos) < 0) {
                if (mFragment != null)
                    Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.seek_error));
                pause();
            } else {
                mVideoManager.play();
                mPlaybackState = PlaybackState.PLAYING;
                if (mFragment != null) {
                    mFragment.setFadingEnabled(true);
                    mFragment.setPlayPauseActionState(1);
                }
                startReportLoop();
            }
        }
    }

    private long currentSkipPos = 0;
    private final Runnable skipRunnable = () -> {
        if (!(isPlaying() || isPaused())) return;

        seek(currentSkipPos);
        currentSkipPos = 0;
    };

    private void skip(int msec) {
        if (hasInitializedVideoManager() && (isPlaying() || isPaused()) && spinnerOff && mVideoManager.getCurrentPosition() > 0) {
            mHandler.removeCallbacks(skipRunnable);
            refreshCurrentPosition();
            currentSkipPos = Utils.getSafeSeekPosition((currentSkipPos == 0 ? mCurrentPosition : currentSkipPos) + msec, getDuration());

            Timber.d("Skip amount requested was %s. Calculated position is %s", msec, currentSkipPos);
            Timber.d("Duration reported as: %s current pos: %s", getDuration(), mCurrentPosition);

            mSeekPosition = currentSkipPos;
            mHandler.postDelayed(skipRunnable, 800);
        }
    }

    public void updateTvProgramInfo() {
        final BaseItemDto channel = getCurrentlyPlayingItem();
        if (channel.getType() == BaseItemKind.TV_CHANNEL) {
            PlaybackControllerHelperKt.getLiveTvChannel(this, channel.getId(), updatedChannel -> {
                BaseItemDto program = updatedChannel.getCurrentProgram();
                if (program != null) {
                    mCurrentProgramEnd = program.getEndDate();
                    mCurrentProgramStart = program.getStartDate();
                    if (mFragment != null) mFragment.updateDisplay();
                }
                return null;
            });
        }
    }

    private long getRealTimeProgress() {
        if (mCurrentProgramStart != null) {
            return Duration.between(mCurrentProgramStart, LocalDateTime.now()).toMillis();
        }
        return 0;
    }

    private long getTimeShiftedProgress() {
        refreshCurrentPosition();
        return !directStreamLiveTv ? mCurrentPosition + (mCurrentTranscodeStartTime - (mCurrentProgramStart == null ? 0 : mCurrentProgramStart.toInstant(ZoneOffset.UTC).toEpochMilli())) : getRealTimeProgress();
    }

    private void startReportLoop() {
        if (mCurrentStreamInfo == null) return;

        stopReportLoop();
        reportingHelper.getValue().reportProgress(mFragment, this, getCurrentlyPlayingItem(), getCurrentStreamInfo(), mCurrentPosition * 10000, false);
        mReportLoop = new Runnable() {
            @Override
            public void run() {
                if (isPlaying()) {
                    refreshCurrentPosition();
                    long currentTime = isLiveTv ? getTimeShiftedProgress() : mCurrentPosition;

                    reportingHelper.getValue().reportProgress(mFragment, PlaybackController.this, getCurrentlyPlayingItem(), getCurrentStreamInfo(), currentTime * 10000, false);
                }
                if (mPlaybackState != PlaybackState.UNDEFINED && mPlaybackState != PlaybackState.IDLE) {
                    mHandler.postDelayed(this, PROGRESS_REPORTING_INTERVAL);
                }
            }
        };
        mHandler.postDelayed(mReportLoop, PROGRESS_REPORTING_INTERVAL);
    }

    private void startPauseReportLoop() {
        stopReportLoop();
        if (mCurrentStreamInfo == null) return;
        reportingHelper.getValue().reportProgress(mFragment, this, getCurrentlyPlayingItem(), mCurrentStreamInfo, mCurrentPosition * 10000, true);
        mReportLoop = new Runnable() {
            @Override
            public void run() {
                BaseItemDto currentItem = getCurrentlyPlayingItem();
                if (currentItem == null) {
                    stopReportLoop();
                    return;
                }

                if (mPlaybackState != PlaybackState.PAUSED) {
                    return;
                }
                refreshCurrentPosition();
                long currentTime = isLiveTv ? getTimeShiftedProgress() : mCurrentPosition;
                if (isLiveTv && !directStreamLiveTv && mFragment != null) {
                    mFragment.setSecondaryTime(getRealTimeProgress());
                }

                reportingHelper.getValue().reportProgress(mFragment, PlaybackController.this, currentItem, getCurrentStreamInfo(), currentTime * 10000, true);
                mHandler.postDelayed(this, PROGRESS_REPORTING_PAUSE_INTERVAL);
            }
        };
        mHandler.postDelayed(mReportLoop, PROGRESS_REPORTING_PAUSE_INTERVAL);
    }

    private void stopReportLoop() {
        if (mHandler != null && mReportLoop != null) {
            mHandler.removeCallbacks(mReportLoop);
        }
    }

    private void initialSeek(final long position) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mVideoManager == null)
                    return;
                if (mVideoManager.getDuration() <= 0) {
                    mHandler.postDelayed(this, 25);
                } else if (mVideoManager.isSeekable()) {
                    seek(position);
                } else {
                    finishedInitialSeek = true;
                }
            }
        });
    }

    private void itemComplete() {
        stop();
        resetPlayerErrors();

        BaseItemDto nextItem = getNextItem();
        BaseItemDto curItem = getCurrentlyPlayingItem();
        if (nextItem == null || curItem == null) {
            endPlayback(true);
            return;
        }

        Timber.d("Moving to next queue item. Index: %s", (mCurrentIndex + 1));
        if (userPreferences.getValue().get(UserPreferences.Companion.getNextUpBehavior()) != NextUpBehavior.DISABLED
                && curItem.getType() != BaseItemKind.TRAILER) {
            mCurrentIndex++;
            videoQueueManager.getValue().setCurrentMediaPosition(mCurrentIndex);
            spinnerOff = false;

            if (mFragment != null) mFragment.showNextUp(nextItem.getId());
            endPlayback();
        } else {
            next();
        }
    }

    @Override
    public void onPlaybackSpeedChange(float newSpeed) {
        // TODO, implement speed change handling
    }

    @Override
    public void onPrepared() {
        if (mCurrentStreamInfo == null) {
            Timber.e("onPrepared: mCurrentStreamInfo is null, cannot continue playback");
            if (mFragment != null) {
                Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_cannot_play));
                mFragment.closePlayer();
            }
            return;
        }
        if (mPlaybackState == PlaybackState.BUFFERING) {
            if (mFragment != null) {
                mFragment.setFadingEnabled(true);
                mFragment.leanbackOverlayFragment.setShouldShowOverlay(false);
            }

            mPlaybackState = PlaybackState.PLAYING;
            mCurrentTranscodeStartTime = mCurrentStreamInfo.getPlayMethod() == PlayMethod.TRANSCODE ? Instant.now().toEpochMilli() : 0;
            startReportLoop();
        }

        Timber.i("onPrepared: Play method: %s", mCurrentStreamInfo.getPlayMethod() == PlayMethod.TRANSCODE ? "Trans" : "Direct");

        if (mPlaybackState == PlaybackState.PAUSED) {
            mPlaybackState = PlaybackState.PLAYING;
        } else {
            if (!burningSubs) {
                Integer currentSubtitleIndex = mCurrentOptions.getSubtitleStreamIndex();
                if (currentSubtitleIndex == null) currentSubtitleIndex = -1;
                PlaybackControllerHelperKt.setSubtitleIndex(this, currentSubtitleIndex, true);
            }

            int eligibleAudioTrack = mDefaultAudioIndex;
            if (mCurrentOptions.getAudioStreamIndex() != null) {
                eligibleAudioTrack = mCurrentOptions.getAudioStreamIndex();
                Timber.d("onPrepared: Using audio track index %d from mCurrentOptions", eligibleAudioTrack);
            } else if (getCurrentMediaSource().getDefaultAudioStreamIndex() != null) {
                eligibleAudioTrack = getCurrentMediaSource().getDefaultAudioStreamIndex();
                Timber.d("onPrepared: Using server default audio track index %d", eligibleAudioTrack);
            } else {
                Timber.w("onPrepared: No audio track index set, using default %d", eligibleAudioTrack);
            }

            int currentTrack = mVideoManager.getExoPlayerTrack(MediaStreamType.AUDIO, getCurrentlyPlayingItem().getMediaStreams());
            if (currentTrack != eligibleAudioTrack) {
                Timber.d("onPrepared: Current audio track %d does not match desired %d, switching", currentTrack, eligibleAudioTrack);
                switchAudioStream(eligibleAudioTrack);
            } else {
                Timber.d("onPrepared: Audio track %d already selected", eligibleAudioTrack);
                // Ensure mCurrentOptions is updated to reflect the actual track
                if (mCurrentOptions.getAudioStreamIndex() == null || !mCurrentOptions.getAudioStreamIndex().equals(currentTrack)) {
                    mCurrentOptions.setAudioStreamIndex(currentTrack);
                    Timber.i("onPrepared: Updated mCurrentOptions audio stream index to %d", currentTrack);
                    MediaSourceInfo mediaSource = getCurrentMediaSource();
                    if (mediaSource != null) {
                        for (MediaStream stream : mediaSource.getMediaStreams()) {
                            if (stream.getType() == MediaStreamType.AUDIO && stream.getIndex() == currentTrack) {
                                if (stream.getLanguage() != null) {
                                    videoQueueManager.getValue().setLastPlayedAudioLanguageIsoCode(stream.getLanguage());
                                    Timber.i("onPrepared: Persisted audio language: %s for index: %d", stream.getLanguage(), currentTrack);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onError() {
        if (mFragment == null) {
            playerErrorEncountered();
            return;
        }

        if (isLiveTv && directStreamLiveTv) {
            Utils.showToast(mFragment.getContext(), mFragment.getString(R.string.msg_error_live_stream));
            directStreamLiveTv = false;
        } else {
            String msg = mFragment.getString(R.string.video_error_unknown_error);
            Timber.e("Playback error - %s", msg);
        }
        playerErrorEncountered();
    }

    @Override
    public void onCompletion() {
        Timber.d("On Completion fired");
        itemComplete();
    }

    @Override
    public void onProgress() {
        refreshCurrentPosition();
        if (isPlaying()) {
            if (!spinnerOff) {
                if (mStartPosition > 0) {
                    initialSeek(mStartPosition);
                    mStartPosition = 0;
                } else {
                    finishedInitialSeek = true;
                    stopSpinner();
                }
            }
        }
        if (mFragment != null)
            mFragment.setCurrentTime(mCurrentPosition);
    }

    public long getDuration() {
        long duration = 0;

        if (hasInitializedVideoManager()) {
            duration = mVideoManager.getDuration();
        } else if (getCurrentlyPlayingItem() != null && getCurrentlyPlayingItem().getRunTimeTicks() != null) {
            duration = getCurrentlyPlayingItem().getRunTimeTicks() / 10000;
        }
        return duration > 0 ? duration : 0;
    }

    public long getBufferedPosition() {
        long bufferedPosition = -1;

        if (hasInitializedVideoManager())
            bufferedPosition = mVideoManager.getBufferedPosition();

        if (bufferedPosition < 0)
            bufferedPosition = getDuration();

        return bufferedPosition;
    }

    public long getCurrentPosition() {
        return !isPlaying() && mSeekPosition != -1 ? mSeekPosition : mCurrentPosition;
    }

    public boolean isPaused() {
        return mPlaybackState == PlaybackState.PAUSED;
    }

    public @NonNull ZoomMode getZoomMode() {
        return hasInitializedVideoManager() ? mVideoManager.getZoomMode() : ZoomMode.FIT;
    }

    public void setZoom(@NonNull ZoomMode mode) {
        if (hasInitializedVideoManager())
            mVideoManager.setZoom(mode);
    }

    public enum PlaybackState {
        PLAYING,
        PAUSED,
        BUFFERING,
        IDLE,
        SEEKING,
        UNDEFINED,
        ERROR
    }
}
