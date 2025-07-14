package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.OverlayStatsBindingBinding
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.dp
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

class StatsAction(
    context: Context,
    customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
    private var isStatsVisible = false
    private var statsOverlay: View? = null
    private var binding: OverlayStatsBindingBinding? = null

    init {
        initializeWithIcon(R.drawable.ic_error)
    }

    override fun handleClickAction(
        playbackController: PlaybackController,
        videoPlayerAdapter: VideoPlayerAdapter,
        context: Context,
        view: View,
    ) {
        if (isStatsVisible) {
            hideStatsOverlay()
        } else {
            showStatsOverlay(playbackController, videoPlayerAdapter, context, view)
        }
    }

    private fun showStatsOverlay(
        playbackController: PlaybackController,
        videoPlayerAdapter: VideoPlayerAdapter,
        context: Context,
        anchorView: View
    ) {
        // Create overlay if it doesn't exist
        if (statsOverlay == null) {
            val rootView = (anchorView.rootView as? ViewGroup)
                ?.findViewById<FrameLayout>(android.R.id.content)
                ?: return

            val inflater = LayoutInflater.from(context)
            binding = OverlayStatsBindingBinding.inflate(inflater, rootView, false)
            statsOverlay = binding?.root?.apply {
                // Position at the top of the screen
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = 16.dp(context)
                    topMargin = margin
                    leftMargin = margin
                    rightMargin = margin
                }
                layoutParams = params

                // Add to root view
                rootView.addView(this)
            }
        }

        // Update stats
        updateStats(playbackController, videoPlayerAdapter, context)

        // Show the overlay
        statsOverlay?.isVisible = true
        isStatsVisible = true
    }

    private fun hideStatsOverlay() {
        statsOverlay?.isVisible = false
        isStatsVisible = false
    }

    private fun updateStats(
        playbackController: PlaybackController,
        videoPlayerAdapter: VideoPlayerAdapter,
        context: Context
    ) {
        val currentMediaSource = playbackController.getCurrentMediaSource() ?: return
        val currentStreamInfo = playbackController.currentStreamInfo

        // Get video stream
        val videoStream = currentMediaSource.mediaStreams
            ?.firstOrNull { it.type == MediaStreamType.VIDEO }

        // Get audio stream
        val audioStream = currentMediaSource.mediaStreams
            ?.firstOrNull { it.type == MediaStreamType.AUDIO }

        // Update UI
        binding?.apply {
            // Video info
            val videoResolution = videoStream?.let { "${it.width}x${it.height}" }
                ?: context.getString(R.string.resolution_na)
            val videoCodec = videoStream?.codec ?: context.getString(R.string.resolution_na)
            val videoBitrate = videoStream?.bitRate?.let { "${it / 1000} kbps" }
                ?: context.getString(R.string.resolution_na)
            val videoProfile = videoStream?.profile?.takeIf { it.isNotBlank() }
                ?: videoStream?.displayTitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.resolution_na)

            videoStats.text = context.getString(
                R.string.video_info,
                videoResolution,
                videoCodec.uppercase(),
                videoBitrate,
                videoProfile
            )

            // Audio info - get the currently selected audio track
            val currentAudioStreamIndex = playbackController.audioStreamIndex
            val selectedAudioStream = currentMediaSource.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == currentAudioStreamIndex }
                ?: audioStream // Fall back to default audio stream if no track is selected

            val audioCodec = selectedAudioStream?.codec ?: context.getString(R.string.resolution_na)
            val audioChannels = selectedAudioStream?.channels?.let { "$it ch" }
                ?: context.getString(R.string.resolution_na)
            val audioBitrate = selectedAudioStream?.bitRate?.let { "${it / 1000} kbps" }
                ?: context.getString(R.string.resolution_na)
            val audioLanguage = selectedAudioStream?.language ?: context.getString(R.string.resolution_na)

            // Show all available audio tracks in the detailed info
            val allAudioTracks = currentMediaSource.mediaStreams
                ?.filter { it.type == MediaStreamType.AUDIO }
                ?.joinToString("\n") { stream ->
                    val isCurrent = stream.index == currentAudioStreamIndex
                    "${if (isCurrent) "→ " else "  "}${stream.language ?: "Unknown"} (${stream.codec ?: "?"}${stream.channels?.let { ", $it ch" } ?: ""})${if (stream.isDefault) " [Default]" else ""}"
                } ?: context.getString(R.string.resolution_na)

            audioStats.text = context.getString(
                R.string.audio_info,
                audioCodec.uppercase(),
                audioChannels,
                audioBitrate,
                audioLanguage
            ) + "\n\nAvailable Tracks:\n$allAudioTracks"

            // Playback info
            val isTranscoding = currentMediaSource.isTranscoding()
            val playMethod = currentStreamInfo?.playMethod?.name ?: "UNKNOWN"
            val container = currentMediaSource.container ?: context.getString(R.string.resolution_na)

            // Build detailed playback info
            val playbackInfoText = buildString {
                // Basic info
                append(context.getString(R.string.playback_method, playMethod))
                append("\n")
                append(context.getString(R.string.container, container))

                // Transcoding details
                if (isTranscoding) {
                    append("\n\n")
                    append(context.getString(R.string.transcoding_details))
                    append("\n")

                    // Transcoding protocol
                    currentMediaSource.transcodingSubProtocol?.let { protocol ->
                        append(context.getString(R.string.transcoding_protocol, protocol))
                        append("\n")
                    }

                    // Add media format information
                    videoStream?.let { stream ->
                        append("\n")
                        val bitrate = stream.bitRate?.let { "${it / 1000} kbps" } ?: "N/A"
                        val framerate = stream.averageFrameRate?.let { "${it.toInt()}fps" } ?: "N/A"
                        val codec = stream.codec?.uppercase() ?: "N/A"
                        val profile = stream.profile?.takeIf { it.isNotBlank() } ?: "N/A"

                        append("Format: $bitrate $codec $profile\n")
                        append("Framerate: $framerate\n")
                    }

                    // Transcoding reasons from the server
                    val reasons = mutableListOf<String>()

                    // Check if video is being transcoded
                    val isVideoTranscoded = videoStream?.isInterlaced == true ||
                            videoStream?.codec?.lowercase() in listOf("hevc", "h265", "vp9", "vp8", "av1")

                    // Check if audio is being transcoded
                    val audioCodec = audioStream?.codec?.lowercase()
                    val isAudioTranscoded = audioCodec in listOf("dts", "truehd", "eac3", "dts-hd", "flac") ||
                            (audioStream?.channels ?: 0) > 2

                    // Check if subtitles are being burned in
                    val hasSubtitles = currentMediaSource.mediaStreams?.any { it.type == MediaStreamType.SUBTITLE } == true

                    // Add the primary reason for transcoding
                    when {
                        isVideoTranscoded -> reasons.add("The video codec is not supported")
                        isAudioTranscoded -> reasons.add("The audio codec is not supported")
                        hasSubtitles -> reasons.add("The subtitle codec is not supported")
                    }

                    // Add any detected reasons
                    if (reasons.isNotEmpty()) {
                        append("\n")
                        append(context.getString(R.string.reason_for_transcoding))
                        reasons.forEach { reason ->
                            append("\n• $reason")
                        }
                    }
                }
            }

            playbackInfo.text = playbackInfoText
        }
    }

    fun dismissPopup() {
        hideStatsOverlay()
    }

    private fun org.jellyfin.sdk.model.api.MediaSourceInfo.isTranscoding(): Boolean {
        // Check if the media source is being transcoded
        return transcodingUrl != null && transcodingUrl?.isNotBlank() == true
    }
}
