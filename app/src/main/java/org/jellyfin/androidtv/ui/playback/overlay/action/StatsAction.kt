package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.OverlayStatsBindingBinding
import org.jellyfin.androidtv.ui.graph.NetworkGraphView
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.getConnectionSpeed
import org.jellyfin.androidtv.ui.playback.getNetworkStats
import org.jellyfin.androidtv.ui.playback.model.NetworkStats
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.dp
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import java.util.Locale

class StatsAction(
    context: Context,
    customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue
) : CustomAction(context, customPlaybackTransportControlGlue) {
    private var isStatsVisible = false
    private var statsOverlay: View? = null
    private var binding: OverlayStatsBindingBinding? = null
    private val networkHandler = Handler(Looper.getMainLooper())
    private var networkGraph: NetworkGraphView? = null
    private var isMonitoringNetwork = false

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
        // Clean up any existing overlay
        stopNetworkMonitoring()

        // Remove existing overlay if it exists
        statsOverlay?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }

        binding = null
        statsOverlay = null
        networkGraph = null

        // Create new overlay
        val rootView = (anchorView.rootView as? ViewGroup)
            ?.findViewById<FrameLayout>(android.R.id.content)
            ?: return

        val inflater = LayoutInflater.from(context)
        binding = OverlayStatsBindingBinding.inflate(inflater, rootView, false)
        networkGraph = binding?.networkGraph
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

        // Update stats
        updateStats(playbackController, videoPlayerAdapter, context)

        // Show the overlay
        statsOverlay?.visibility = View.VISIBLE

        // Start network monitoring
        startNetworkMonitoring(playbackController)

        isStatsVisible = true
    }

    private fun hideStatsOverlay() {
        statsOverlay?.visibility = View.GONE
        stopNetworkMonitoring()
        isStatsVisible = false

        // Clean up the view hierarchy
        statsOverlay?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            statsOverlay = null
            binding = null
            networkGraph = null
        }
    }

    private var networkMonitor: Runnable? = null

    private fun startNetworkMonitoring(playbackController: PlaybackController) {
        if (isMonitoringNetwork) return
        isMonitoringNetwork = true

        // Get initial stats to avoid spikes
        playbackController.getNetworkStats()

        // Reset the graph when starting monitoring
        networkGraph?.post {
            networkGraph?.reset()
        }

        networkMonitor = object : Runnable {
            private var lastTime = System.currentTimeMillis()
            private var lastDownloadSpeed = 0f
            private var lastUploadSpeed = 0f
            private val smoothingFactor = 0.7f // Higher = smoother but more lag

            override fun run() {
                if (!isMonitoringNetwork) return

                try {
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastTime).coerceAtLeast(1)

                    // Get current network stats (already in bytes per second)
                    val stats = playbackController.getNetworkStats()

                    // Convert to kbps (kilobits per second)
                    val downloadKbps = (stats.bytesRead * 8) / 1000f
                    val uploadKbps = (stats.bytesWritten * 8) / 1000f

                    // Apply exponential moving average for smoothing
                    val smoothedDownload = when {
                        lastDownloadSpeed == 0f -> downloadKbps.coerceAtLeast(0f)
                        else -> (downloadKbps * (1 - smoothingFactor)) + (lastDownloadSpeed * smoothingFactor)
                    }.coerceAtLeast(0f)

                    val smoothedUpload = when {
                        lastUploadSpeed == 0f -> uploadKbps.coerceAtLeast(0f)
                        else -> (uploadKbps * (1 - smoothingFactor)) + (lastUploadSpeed * smoothingFactor)
                    }.coerceAtLeast(0f)

                    // Update last values
                    lastDownloadSpeed = smoothedDownload
                    lastUploadSpeed = smoothedUpload
                    lastTime = currentTime

                    // Update graph on UI thread
                    networkGraph?.post {
                        try {
                            networkGraph?.addData(smoothedDownload, smoothedUpload)
                        } catch (e: Exception) {
                            // Silently handle errors
                        }
                    }

                } catch (e: Exception) {
                    // Silently handle errors but continue monitoring
                } finally {
                    // Always schedule the next update
                    if (isMonitoringNetwork) {
                        networkHandler.postDelayed(this, 500)
                    }
                }
            }
        }

        // Start monitoring with a small delay to allow UI to update
        networkMonitor?.let { networkHandler.postDelayed(it, 100) }
    }

    private fun stopNetworkMonitoring() {
        isMonitoringNetwork = false
        networkMonitor?.let { monitor ->
            networkHandler.removeCallbacks(monitor)
        }
        networkMonitor = null

        // Reset the graph when stopping monitoring
        networkGraph?.post {
            networkGraph?.reset()
        }
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
            val videoProfile = videoStream?.profile?.takeIf { it.isNotBlank() }
                ?: videoStream?.displayTitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.resolution_na)

            // Format video info as a clean list
            val videoInfo = StringBuilder().apply {
                // Media file title
                currentMediaSource.name?.takeIf { it.isNotBlank() }?.let { name ->
                    append("• File: $name\n")
                }

                // Resolution and codec info
                append("• Resolution: $videoResolution\n")
                append("• Codec: ${videoCodec.uppercase()}\n")

                // Video profile and bit depth
                if (videoProfile.isNotBlank()) {
                    append("• Profile: $videoProfile\n")
                }
                videoStream?.bitDepth?.let { bitDepth ->
                    append("• Bit Depth: ${bitDepth}bit\n")
                }

                // Video range (HDR, SDR, etc.)
                val videoRange = when {
                    !videoStream?.videoDoViTitle.isNullOrBlank() -> {
                        // For Dolby Vision content, check if it's combined with HDR10 or HDR10+
                        val hdrType = videoStream?.videoRangeType?.let { rangeType ->
                            when (rangeType.serialName.uppercase()) {
                                "DOVIWITHHDR10" -> "HDR10"
                                "DOVIWITHHDR10PLUS" -> "HDR10+"
                                "DOVIWITHHLG" -> "HLG"
                                else -> null
                            }
                        }
                        if (hdrType != null) "Dolby Vision $hdrType" else "Dolby Vision"
                    }
                    videoStream?.videoRangeType != null -> when (videoStream.videoRangeType!!.serialName.uppercase()) {
                        "DOLBY_VISION" -> "Dolby Vision"
                        "HDR10" -> "HDR10"
                        "HDR10_PLUS" -> "HDR10+"
                        "HLG" -> "HLG"
                        "SDR" -> "SDR"
                        else -> videoStream.videoRangeType!!.serialName
                    }
                    else -> null
                }
                append("• Range: $videoRange\n")

                // Bitrate
                videoStream?.bitRate?.let { bitrate ->
                    val bitrateInMbps = bitrate / 1000000.0
                    append("• Bitrate: ${String.format(Locale.US, "%.2f", bitrateInMbps)} Mbps\n")
                }

                // Framerate
                videoStream?.averageFrameRate?.let { fps ->
                    append("• Framerate: ${fps.toInt()}fps\n")
                }

                // File size
                currentMediaSource.size?.let { sizeInBytes ->
                    val sizeInGB = sizeInBytes / (1024.0 * 1024 * 1024)
                    append("• File Size: ${String.format(Locale.US, "%.2f", sizeInGB)} GB")
                }
            }.toString()

            videoStats.text = videoInfo

            // Get all audio streams
            val audioStreams = currentMediaSource.mediaStreams
                ?.filter { it.type == MediaStreamType.AUDIO }
                ?.sortedBy { it.index } ?: emptyList()

            val currentAudioStreamIndex = playbackController.audioStreamIndex

            // Format audio info with all available tracks
            val audioInfo = StringBuilder().apply {
                if (audioStreams.isEmpty()) {
                    append(context.getString(R.string.resolution_na))
                } else {
                    audioStreams.forEach { stream ->
                        val isSelected = stream.index == currentAudioStreamIndex
                        val prefix = if (isSelected) "▶ " else "  "

                        val codec = stream.codec?.uppercase() ?: "N/A"
                        val channels = stream.channels?.let {
                            when (it) {
                                1 -> "Mono"
                                2 -> "Stereo"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> "$it ch"
                            }
                        } ?: "N/A"

                        val language = stream.language?.takeIf { it.length >= 2 }?.take(2)?.uppercase() ?: "--"
                        val title = stream.displayTitle?.takeIf { it.isNotBlank() } ?: "Track ${stream.index + 1}"

                        append(prefix)
                        append("$language • $codec • $channels • $title")

                        if (isSelected) {
                            append(" (Selected)")
                        }

                        append("\n")
                    }
                    // Remove the last newline
                    if (audioStreams.isNotEmpty()) {
                        setLength(length - 1)
                    }
                }
            }.toString()

            audioStats.text = audioInfo

            // Playback info
            val isTranscoding = currentMediaSource.isTranscoding()
            val playMethod = currentStreamInfo?.playMethod?.name?.replace("_", " ")?.lowercase()?.capitalize()
                ?: "Unknown"
            val container = currentMediaSource.container?.uppercase() ?: context.getString(R.string.resolution_na)

            // Set playback method and state
            playbackMethod.text = "$playMethod • $container"

            // Build transcoding details if needed
            val transcodingDetails = buildString {
                if (isTranscoding) {
                    // Add transcoding protocol if available
                    currentMediaSource.transcodingSubProtocol?.let { protocol ->
                        append("Protocol: ${protocol.toString().toUpperCase()}\n")
                    }

                    // Add media format information
                    videoStream?.let { stream ->
                        val bitrate = stream.bitRate?.let { "${it / 1000} kbps" } ?: "N/A"
                        val framerate = stream.averageFrameRate?.let { "${it.toInt()}fps" } ?: "N/A"
                        val codec = stream.codec?.uppercase() ?: "N/A"
                        val profile = stream.profile?.takeIf { it.isNotBlank() } ?: "N/A"

                        append("Video: $bitrate $codec $profile\n")
                        append("Framerate: $framerate\n")
                    }

                    // Transcoding reasons
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
                        isVideoTranscoded -> reasons.add("Video codec is not supported")
                        isAudioTranscoded -> reasons.add("Audio codec is not supported")
                        hasSubtitles -> reasons.add("Subtitle codec is not supported")
                    }

                    // Add any detected reasons
                    if (reasons.isNotEmpty()) {
                        append("\nTranscoding:\n")
                        reasons.forEach { reason ->
                            append("• $reason\n")
                        }
                    }
                } else {
                    append("Direct Play")
                }
            }

            playbackState.text = transcodingDetails
        }
    }

    fun dismissPopup() {
        hideStatsOverlay()
    }

    private fun org.jellyfin.sdk.model.api.MediaSourceInfo.isTranscoding(): Boolean {
        // Check if the media source is being transcoded
        val url = transcodingUrl
        return !url.isNullOrBlank()
    }
}
