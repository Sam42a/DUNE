package org.jellyfin.androidtv.util

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber
import android.view.Display
import android.view.WindowManager

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

    // Dolby Vision profile names as reported by MediaCodec
    private val DV_PROFILE_NAMES = mapOf(
        4 to "dvhe.04", // Profile 4 (MEL)
        5 to "dvhe.05", // Profile 5 (STB)
        7 to "dvhe.07", // Profile 7 (8.4)
        8 to "dvhe.08", // Profile 8.1
        9 to "dvhe.09"  // Profile 8.2
    )

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
     * Uses Display.getHdrCapabilities()
     * Falls back to MediaCodecList
     *
     * Note: The logic for checking Dolby Vision support using [Display.getHdrCapabilities]
     *       was inspired by the Nova Video Player's approach found in Player.java.
     *       Copyright 2017 Archos SA, Copyright 2020 Courville Software.
     *       Licensed under the Apache License, Version 2.0.
     *
     * @param context The application or activity context, required to access the display
     * @return True if Dolby Vision is supported by the display or underlying codec, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP) // Minimum for MediaCodecList, but HDR checks need API 24/26
    fun supportsDolbyVision(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { // API 24 (N)
            // API 24 is required for Display.getHdrCapabilities
            // Fallback to MediaCodecList check for older versions if needed
            Timber.w("API < 24, HDR capabilities check not available. Falling back to MediaCodec check.")
            return checkDolbyVisionCodecSupport()
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = windowManager?.defaultDisplay // Use default display, which should be the main TV screen

        if (display != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
                // Check if the display reports HDR support in general (optional, adds extra check)
                if (display.isHdr) {
                    Timber.d("Display reports general HDR support.")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                val hdrCaps = display.hdrCapabilities
                if (hdrCaps != null) {
                    val supportedHdrTypes = hdrCaps.supportedHdrTypes
                    Timber.d("Display supported HDR types: ${supportedHdrTypes.contentToString()}")
                    // Check specifically for Dolby Vision support
                    val hasDoVi = supportedHdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
                    if (hasDoVi) {
                        Timber.d("Display reports Dolby Vision support.")
                    } else {
                        Timber.d("Display does not report Dolby Vision support.")
                    }
                    return hasDoVi
                } else {
                    Timber.w("Display HDR capabilities are null.")
                }
            } else {
                Timber.w("API level too low for getHdrCapabilities().")
            }
        } else {
            Timber.w("Could not get default display from WindowManager.")
        }

        // Fallback if display check fails or is not available
        Timber.d("Falling back to MediaCodecList check for Dolby Vision support.")
        return checkDolbyVisionCodecSupport()
    }

    // Fallback function using MediaCodecList, similar to the original
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkDolbyVisionCodecSupport(): Boolean {
        val mimeType = "video/dolby-vision"
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (codec in codecList.codecInfos) {
            if (codec.isEncoder) continue
            for (type in codec.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    Timber.d("Found Dolby Vision support via MediaCodec: ${codec.name}")
                    return true
                }
            }
        }
        Timber.d("Dolby Vision not found via MediaCodecList.")
        return false
    }

    /**
     * Get the best HDR type supported by the device
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getBestHdrType(context: Context): String? {
        return when {
            supportsDolbyVision(context) -> HDR_TYPE_DOLBY_VISION
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
    /**
     * Get the list of supported Dolby Vision profiles
     * This checks both the display capabilities and codec-level support
     *
     * @param context The application or activity context
     * @return List of supported Dolby Vision profile strings (e.g., "dvhe.04", "dvhe.05", etc.)
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun getSupportedDolbyVisionProfiles(context: Context): List<String> {
        if (!supportsDolbyVision(context)) return emptyList()

        val supportedProfiles = mutableListOf<String>()
        
        // Check codec capabilities for specific profile support
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (codec in codecList.codecInfos) {
            if (codec.isEncoder) continue
            
            try {
                for (type in codec.supportedTypes) {
                    if (type.equals(MIME_VIDEO_DOLBY_VISION, ignoreCase = true) ||
                        type.equals(MIME_VIDEO_HEVC, ignoreCase = true)) {
                        val caps = codec.getCapabilitiesForType(type)
                        
                        for (profileLevel in caps.profileLevels) {
                            // The profile field contains the Dolby Vision profile information
                            val profileNumber = when (profileLevel.profile) {
                                // Map MediaCodec profile constants to DV profile numbers
                                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr -> 4  // MEL
                                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn -> 5  // STB
                                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb -> 7  // 8.4
                                8 -> 8 // 8.1 DolbyVisionProfileDvheDtbStn 
                                9 -> 9 // 8.2 DolbyVisionProfileDvavSe
                                else -> null
                            }
                            
                            if (profileNumber != null) {
                                DV_PROFILE_NAMES[profileNumber]?.let { supportedProfiles.add(it) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking Dolby Vision profile support for codec: ${codec.name}")
            }
        }

        return supportedProfiles.distinct()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun logHdrCapabilities(context: Context) {
        Timber.d("HDR Capabilities:")
        Timber.d("  HDR10: ${supportsHdr10()}")
        Timber.d("  HLG: ${supportsHlg()}")
        Timber.d("  Dolby Vision: ${supportsDolbyVision(context)}")
        if (supportsDolbyVision(context)) {
            Timber.d("  Supported DV profiles: ${getSupportedDolbyVisionProfiles(context).joinToString()}")
        }
        Timber.d("  Best HDR type: ${getBestHdrType(context) ?: "None"}")
    }
}
