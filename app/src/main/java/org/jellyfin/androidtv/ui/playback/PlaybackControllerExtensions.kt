package org.jellyfin.androidtv.ui.playback

import org.jellyfin.androidtv.ui.playback.model.NetworkStats
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import android.net.TrafficStats
import android.os.Process

/**
 * Get current network statistics from the playback controller
 */
// Cache the last values to calculate deltas
private var lastBytesRead = 0L
private var lastBytesWritten = 0L
private var lastTimestamp = System.currentTimeMillis()

fun PlaybackController.getNetworkStats(): NetworkStats {
    // Get current traffic stats for the app
    val uid = Process.myUid()
    val bytesReceived = TrafficStats.getUidRxBytes(uid)
    val bytesSent = TrafficStats.getUidTxBytes(uid)
    
    // Calculate deltas
    val currentTime = System.currentTimeMillis()
    val timeDelta = (currentTime - lastTimestamp).coerceAtLeast(1)
    
    // Calculate bytes per second
    val bytesRead = if (bytesReceived != TrafficStats.UNSUPPORTED.toLong()) {
        if (lastBytesRead > 0) (bytesReceived - lastBytesRead) * 1000 / timeDelta else 0L
    } else 0L
    
    val bytesWritten = if (bytesSent != TrafficStats.UNSUPPORTED.toLong()) {
        if (lastBytesWritten > 0) (bytesSent - lastBytesWritten) * 1000 / timeDelta else 0L
    } else 0L
    
    // Update last values
    if (bytesReceived != TrafficStats.UNSUPPORTED.toLong()) lastBytesRead = bytesReceived
    if (bytesSent != TrafficStats.UNSUPPORTED.toLong()) lastBytesWritten = bytesSent
    lastTimestamp = currentTime
    
    return NetworkStats(
        bytesRead = bytesRead,
        bytesWritten = bytesWritten,
        timestamp = currentTime
    )
}

/**
 * Get the current connection speed in bps (bits per second)
 */
fun PlaybackController.getConnectionSpeed(): Long {
    return try {
        // Use reflection to access the internal media session
        val mediaSessionField = this.javaClass.getDeclaredField("mMediaSession")
        mediaSessionField.isAccessible = true
        val mediaSession = mediaSessionField.get(this)
        
        // Get the current playback state
        val getPlaybackState = mediaSession.javaClass.getMethod("getPlaybackState")
        val playbackState = getPlaybackState.invoke(mediaSession)
        
        // Get the playback info which contains the bitrate
        val getPlaybackInfo = playbackState.javaClass.getMethod("getPlaybackInfo")
        val playbackInfo = getPlaybackInfo.invoke(playbackState)
        
        // Get the current bitrate in bps
        val getBitrate = playbackInfo.javaClass.getMethod("getBitrate")
        (getBitrate.invoke(playbackInfo) as? Int)?.toLong() ?: 0L
    } catch (e: Exception) {
        e.printStackTrace()
        0L
    }
}

// Helper methods removed as they're no longer needed
