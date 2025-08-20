package org.jellyfin.playback.media3.exoplayer.mapping

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object SubtitleMimeTypeMapper {
	/**
	 * Comprehensive mapping of subtitle file extensions to MIME types.
	 */
	@JvmStatic
	val ffmpegSubtitleMimeTypes = mapOf(
		"mp4" to MimeTypes.VIDEO_MP4,
		"ass" to MimeTypes.TEXT_SSA,
		"dvbsub" to MimeTypes.APPLICATION_DVBSUBS,
		"idx" to MimeTypes.APPLICATION_VOBSUB,
		"pgs" to MimeTypes.APPLICATION_PGS,
		"pgssub" to MimeTypes.APPLICATION_PGS,
		"srt" to MimeTypes.APPLICATION_SUBRIP,
		"ssa" to MimeTypes.TEXT_SSA,
		"subrip" to MimeTypes.APPLICATION_SUBRIP,
		"vtt" to MimeTypes.TEXT_VTT,
		"ttml" to MimeTypes.APPLICATION_TTML,
		"webvtt" to MimeTypes.TEXT_VTT,
	)

	/**
	 * Backward-compatible function to maintain existing method signature
	 */
	@JvmStatic
	fun getFfmpegSubtitleMimeType(
		codec: String,
		fallback: String = codec
	): String = codec.lowercase().let { normalizedCodec ->
		ffmpegSubtitleMimeTypes[normalizedCodec]
			?: MimeTypes.getTextMediaMimeType(normalizedCodec)
			?: fallback
	}

	/**
	 * Additional utility methods
	 */
	@JvmStatic
	fun getMimeType(
		codec: String,
		fallback: String = codec
	): String = getFfmpegSubtitleMimeType(codec, fallback)

	@JvmStatic
	fun isKnownSubtitleFormat(codec: String): Boolean =
		codec.lowercase() in ffmpegSubtitleMimeTypes
}

// Optional extension function for convenience
fun String.toSubtitleMimeType(fallback: String? = null): String =
	SubtitleMimeTypeMapper.getFfmpegSubtitleMimeType(this, fallback ?: this)
