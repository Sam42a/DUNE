package org.jellyfin.androidtv.ui.presentation

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import timber.log.Timber
import android.view.View


class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		fun setItem(row: MyDetailsOverviewRow) {
			setTitle(row.item.name)

			InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
			binding.fdGenreRow.text = row.item.genres?.joinToString(" / ")

			binding.infoTitle1.text = row.infoItem1?.label
			binding.infoValue1.text = row.infoItem1?.value

			binding.infoTitle2.text = row.infoItem2?.label
			binding.infoValue2.text = row.infoItem2?.value

			binding.infoTitle3.text = row.infoItem3?.label
			binding.infoValue3.text = row.infoItem3?.value

			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
				binding.fdGenreRow.isVisible = false
			}

			val resolution = getResolutionLabel(row.item.mediaSources?.firstOrNull())
			binding.fdResolution.text = resolution
			binding.fdResolution.visibility = if (resolution != null) View.VISIBLE else View.GONE

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				binding.fdButtonRow.addView(button)
			}
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			binding.infoValue3.text = text
		}

		private fun getResolutionLabel(mediaSource: MediaSourceInfo?): String? {
			if (mediaSource == null) return null

			val videoStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return null
			val audioStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

			val width = videoStream.width ?: return null
			val height = videoStream.height ?: return null

			// Get resolution label using the same logic as BaseItemInfoRow
			val resolution = when {
				width >= 7600 || height >= 4300 -> "8K"
				width >= 3800 || height >= 2000 -> "4K"
				width >= 2500 || height >= 1400 -> "QHD"
				width >= 1800 || height >= 1000 -> "FHD"
				width >= 1280 || height >= 720 -> "HD"
				width >= 1200 || height >= 700 -> "SD"
				width >= 600 || height >= 400 -> "SD"
				else -> "SD"
			}

			// Get video codec - simplified to match BaseItemInfoRow approach
			val videoCodec = when (videoStream.codec?.uppercase()) {
				"H264", "AVC" -> "H.264"
				"HEVC", "H265" -> "H.265"
				"VP9" -> "VP9"
				"AV1" -> "AV1"
				else -> videoStream.codec?.uppercase() ?: ""
			}

			// Get video range type - handle Dolby Vision with HDR types
			val range = when {
				!videoStream.videoDoViTitle.isNullOrBlank() -> {
					// For Dolby Vision content, check if it's combined with HDR10 or HDR10+
					val hdrType = when (videoStream.videoRangeType?.serialName?.uppercase()) {
						"DOVIWITHHDR10" -> "HDR10"
						"DOVIWITHHDR10PLUS" -> "HDR10+"
						"DOVIWITHHLG" -> "HLG"
						else -> null
					}
					if (hdrType != null) "DV $hdrType" else "DV"
				}
				videoStream.videoRangeType != null -> when (videoStream.videoRangeType!!.serialName.uppercase()) {
					"DOLBY_VISION" -> "DV"
					"HLG" -> "HLG"
					"HDR10_PLUS" -> "HDR10+"
					"HDR10" -> "HDR10"
					"HDR" -> "HDR"
					"SDR" -> "SDR"
					else -> null
				}
				else -> null
			}

			// Get audio information if available
			val audioInfo = audioStream?.let { audio ->
				val audioCodec = when (audio.codec?.uppercase()) {
					"AAC" -> "AAC"
					"AC3" -> "AC3"
					"EAC3" -> "E-AC3"
					"DTS" -> "DTS"
					"DTS-HD" -> "DTS-HD"
					"DTS-HD MA" -> "DTS-HD MA"
					"DTS-X" -> "DTS-X"
					"TRUEHD" -> "Dolby TrueHD"
					"OPUS" -> "Opus"
					"VORBIS" -> "Vorbis"
					"MP3" -> "MP3"
					else -> audio.codec?.uppercase() ?: ""
				}

				// Get audio language (first 2 characters of the language code, e.g., "en" from "eng")
				val language = audio.language?.takeIf { it.length >= 2 }?.take(2)?.uppercase()

				// Format channels with layout names
				val channels = when (audio.channels) {
					null -> ""
					1 -> "Mono"
					2 -> "Stereo"
					3 -> "2.1"
					4 -> "Quad"
					5 -> "4.1"
					6 -> "5.1"
					7 -> "6.1"
					8 -> "7.1"
					9 -> "7.2"
					10 -> "9.1"
					11 -> "9.2"
					12 -> "11.1"
					else -> "${audio.channels}ch"
				}

				// Add common audio layout names
				val layout = when (audio.channels) {
					6 -> when (audio.channelLayout?.uppercase()) {
						"5.1", "5.1(SIDE)" -> "5.1"
						"5.1(BACK)" -> "5.1 (Back)"
						else -> null
					}
					8 -> when (audio.channelLayout?.uppercase()) {
						"7.1", "7.1(WIDE)" -> "7.1"
						"7.1(TOP)" -> "7.1 (Top)"
						"7.1(WIDE-SIDE)" -> "7.1 (Wide)"
						else -> null
					}
					else -> null
				} ?: channels

				listOf(audioCodec, language, layout)
			} ?: emptyList()

			// Combine all parts with forward slashes, filtering out null or empty strings
			return (listOf(resolution, videoCodec, range) + audioInfo)
				.filter { !it.isNullOrEmpty() }
				.joinToString(" / ")
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context)
		viewHolder = ViewHolder(view, markdownRenderer)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder?, item: Any?) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
