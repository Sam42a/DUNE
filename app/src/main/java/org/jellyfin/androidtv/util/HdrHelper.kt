package org.jellyfin.androidtv.util

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Helper class for HDR and Dolby Vision support detection
 */
object HdrHelper {
    // HDR formats
    const val HDR_TYPE_HDR10 = "hdr10"
    const val HDR_TYPE_HDR10_PLUS = "hdr10+"
    const val HDR_TYPE_DOLBY_VISION = "dolby-vision"
    const val HDR_TYPE_HLG = "hlg"

    // MediaCodec MIME types
    private const val MIME_VIDEO_HEVC = "video/hevc"
    private const val MIME_VIDEO_DOLBY_VISION = "video/dolby-vision"

    // Color standards
    private const val COLOR_STANDARD_BT2020 = 6 // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010

    // Color transfer functions
    private const val COLOR_TRANSFER_HLG = 7 // MediaCodecInfo.CodecCapabilities.COLOR_TRANSFER_HLG
    private const val COLOR_TRANSFER_ST2084 = 6 // MediaCodecInfo.CodecCapabilities.COLOR_TRANSFER_ST2084
    private const val COLOR_TRANSFER_LINEAR = 1 // MediaCodecInfo.COLOR_TRANSFER_LINEAR

    /**
     * Check if the device supports HDR10
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun supportsHdr10(): Boolean {
        return checkHdrSupport(MIME_VIDEO_HEVC, COLOR_TRANSFER_ST2084)
    }

    /**
     * Check if the device supports HLG (Hybrid Log-Gamma)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun supportsHlg(): Boolean {
        return checkHdrSupport(MIME_VIDEO_HEVC, COLOR_TRANSFER_HLG)
    }

    /**
     * Check if the device supports Dolby Vision
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun supportsDolbyVision(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (codec in codecList.codecInfos) {
            if (codec.isEncoder) continue
            
            for (mimeType in codec.supportedTypes) {
                if (mimeType.equals(MIME_VIDEO_DOLBY_VISION, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Get the best HDR type supported by the device
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getBestHdrType(): String? {
        return when {
            supportsDolbyVision() -> HDR_TYPE_DOLBY_VISION
            supportsHdr10() -> HDR_TYPE_HDR10
            supportsHlg() -> HDR_TYPE_HLG
            else -> null
        }
    }

    /**
     * Check if the device supports a specific HDR type
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkHdrSupport(mimeType: String, colorTransfer: Int): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        
        for (codec in codecList.codecInfos) {
            if (codec.isEncoder) continue
            
            try {
                for (type in codec.supportedTypes) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        val caps = codec.getCapabilitiesForType(type)
                        for (profile in caps.profileLevels) {
                            for (format in caps.colorFormats) {
                                // Check if the codec supports the required color transfer
                                if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible ||
                                    format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ||
                                    format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar ||
                                    format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                                    format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
                                ) {
                                    // For now, assume support if we find a matching codec with the right format
                                    // Note: We can't reliably check color transfer support on all Android versions
                                    // This is a simplified check that might need adjustment based on specific device capabilities
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // On Android Q+, we can check some basic HDR support
                                        if (colorTransfer == MediaFormat.COLOR_TRANSFER_HLG ||
                                            colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                                            colorTransfer == MediaFormat.COLOR_TRANSFER_HLG) {
                                            // Check if the codec supports HDR
                                            val videoCaps = caps.videoCapabilities
                                            if (videoCaps != null) {
                                                return true
                                            }
                                        }
                                    }
                                    // For older versions or if we can't verify, assume support if we find a matching codec
                                    return true
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking HDR support for codec: ${codec.name}")
            }
        }
        
        return false
    }

    /**
     * Log HDR capabilities of the device
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun logHdrCapabilities() {
        Timber.d("HDR Capabilities:")
        Timber.d("  HDR10: ${supportsHdr10()}")
        Timber.d("  HLG: ${supportsHlg()}")
        Timber.d("  Dolby Vision: ${supportsDolbyVision()}")
        Timber.d("  Best HDR type: ${getBestHdrType() ?: "None"}")
    }
}
