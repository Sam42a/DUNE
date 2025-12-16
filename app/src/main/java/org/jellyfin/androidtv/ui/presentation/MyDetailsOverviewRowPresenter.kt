package org.jellyfin.androidtv.ui.presentation

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.data.model.InfoItem
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.*
import android.widget.TextView

class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		return ViewHolder(DetailRowView(parent.context), markdownRenderer)
	}

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		if (holder is ViewHolder && item is MyDetailsOverviewRow) {
			holder.bind(item)
		}
	}

	@Suppress("UNUSED_PARAMETER")
	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit


	fun getViewHolder(): ViewHolder? {

		return null
	}

	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {

		private val binding get() = detailRowView.binding

		private var lastSummary: String? = null
		private var lastRenderedSummary: CharSequence? = null

		fun bind(row: MyDetailsOverviewRow) {
			bindTitle(row)
			bindInfo(row)
			bindSummary(row)
			bindImage(row)
			bindResolution(row)
			bindButtons(row)
		}

		fun setSummary(summary: String?) {
			lastSummary = summary
			lastRenderedSummary = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
			binding.fdSummaryText.text = lastRenderedSummary
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setItem(row: MyDetailsOverviewRow) {
			bind(row)
		}

		fun setInfoValue3(value: String?) {
			// This method is kept for compatibility but the individual info TextViews are now hidden
			// The info is displayed as a merged line in infoTitle1
			// If needed, this could be updated to modify the merged info line
		}

		private fun bindTitle(row: MyDetailsOverviewRow) {
			binding.fdTitle.text = row.item.name
		}

		private fun bindInfo(row: MyDetailsOverviewRow) {
			binding.fdMainInfoRow.removeAllViews()
			val resolutionText = MediaLabelFormatter.format(
				row.item.mediaSources?.firstOrNull()
			)

			if (!resolutionText.isNullOrEmpty()) {
				val resolutionTextView = android.widget.TextView(view.context).apply {
					text = resolutionText
					setTextColor(androidx.core.content.ContextCompat.getColor(view.context, org.jellyfin.androidtv.R.color.basic_button_text))
					textSize = 12f
					setTypeface(android.graphics.Typeface.SANS_SERIF)
					gravity = android.view.Gravity.CENTER_VERTICAL
				}
				val params = android.widget.LinearLayout.LayoutParams(
					android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
					android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
				)
				params.gravity = android.view.Gravity.CENTER_VERTICAL
				resolutionTextView.layoutParams = params
				binding.fdMainInfoRow.addView(resolutionTextView)
			}

			InfoLayoutHelper.addInfoRow(
				view.context,
				row.item,
				row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex),
				binding.fdMainInfoRow,
				false
			)

			if (!resolutionText.isNullOrEmpty()) {
				val bulletTextView = android.widget.TextView(view.context).apply {
					text = "  •  "
					setTextColor(androidx.core.content.ContextCompat.getColor(view.context, org.jellyfin.androidtv.R.color.basic_button_text))
					textSize = 16f
					setTypeface(android.graphics.Typeface.SANS_SERIF)
					gravity = android.view.Gravity.CENTER_VERTICAL
				}
				val params = android.widget.LinearLayout.LayoutParams(
					android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
					android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
				)
				params.gravity = android.view.Gravity.CENTER_VERTICAL
				bulletTextView.layoutParams = params
				binding.fdMainInfoRow.addView(bulletTextView, 1)
			}

			binding.fdGenreRow.apply {
				text = row.item.genres?.joinToString("  •  ")
				isVisible = row.item.type != BaseItemKind.PERSON
			}

			val mergedInfo = buildMergedInfoLine(row.infoItem1, row.infoItem2, row.infoItem3)

			binding.infoTitle1.apply {
				text = mergedInfo
				isVisible = mergedInfo.isNotEmpty()
			}

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 12
			}
		}

		private fun buildMergedInfoLine(vararg infoItems: InfoItem?): String {
			return infoItems
				.filterNotNull()
				.filter { it.label?.isNotEmpty() == true && it.value?.isNotEmpty() == true }
				.map { "${it.label} • ${it.value}" }
				.joinToString("  •  ")
		}

		private fun bindSummary(row: MyDetailsOverviewRow) {
			val summary = row.summary
			if (summary == lastSummary) {
				binding.fdSummaryText.text = lastRenderedSummary
				return
			}

			lastSummary = summary
			lastRenderedSummary = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
			binding.fdSummaryText.text = lastRenderedSummary
		}

		private fun bindImage(row: MyDetailsOverviewRow) {
			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)
		}

		private fun bindResolution(row: MyDetailsOverviewRow) {
		}

		private fun bindButtons(row: MyDetailsOverviewRow) {
			if (binding.fdButtonRow.childCount == row.actions.size) return

			binding.fdButtonRow.removeAllViews()
			row.actions.forEach { button ->
				(button.parent as? ViewGroup)?.removeView(button)
				binding.fdButtonRow.addView(button)
			}
		}
	}
}

/**
 * Extracted formatter to keep Presenter UI-only
 */
object MediaLabelFormatter {

	fun format(mediaSource: MediaSourceInfo?): String? {
		if (mediaSource == null) return null

		val video = mediaSource.mediaStreams?.firstOrNull {
			it.type == MediaStreamType.VIDEO
		} ?: return null

		val audio = mediaSource.mediaStreams?.firstOrNull {
			it.type == MediaStreamType.AUDIO
		}

		val resolution = formatResolution(video.width, video.height)
		val videoCodec = formatVideoCodec(video.codec)
		val range = formatVideoRange(video)

		val audioInfo = audio?.let {
			listOf(
				formatAudioCodec(it.codec),
				it.language?.take(2)?.uppercase(),
				formatAudioLayout(it)
			)
		}.orEmpty()

		return (listOf(resolution, videoCodec, range) + audioInfo)
			.filter { !it.isNullOrEmpty() }
			.joinToString("  •  ")
	}

	private fun formatResolution(width: Int?, height: Int?): String {
		if (width == null || height == null) return "SD"
		return when {
			width >= 7600 || height >= 4300 -> "8K"
			width >= 3800 || height >= 2000 -> "4K"
			width >= 2500 || height >= 1400 -> "QHD"
			width >= 1800 || height >= 1000 -> "FHD"
			width >= 1280 || height >= 720 -> "HD"
			else -> "SD"
		}
	}

	private fun formatVideoCodec(codec: String?): String =
		when (codec?.uppercase()) {
			"H264", "AVC" -> "H.264"
			"HEVC", "H265" -> "H.265"
			"VP9" -> "VP9"
			"AV1" -> "AV1"
			else -> codec?.uppercase().orEmpty()
		}

	private fun formatVideoRange(video: MediaStream): String? {
		if (!video.videoDoViTitle.isNullOrBlank()) {
			return when (video.videoRangeType) {
				VideoRangeType.DOVI_WITH_HDR10 -> "Dolby Vision Hdr10"
				VideoRangeType.HDR10_PLUS -> "Dolby Vision Hdr10+"
				VideoRangeType.DOVI_WITH_HLG -> "Dolby Vision Hlg"
				else -> "Dolby Vision"
			}
		}

		return when (video.videoRangeType) {
			VideoRangeType.DOVI -> "Dolby Vision"
			VideoRangeType.HDR10 -> "HDR10"
			VideoRangeType.HDR10_PLUS -> "HDR10+"
			VideoRangeType.HLG -> "HLG"
			VideoRangeType.SDR -> "SDR"
			else -> null
		}
	}

	private fun formatAudioCodec(codec: String?): String =
		when (codec?.uppercase()) {
			"AAC" -> "AAC"
			"AC3" -> "AC3"
			"EAC3" -> "E-AC3"
			"DTS" -> "DTS"
			"DTS-HD" -> "DTS-HD"
			"DTS-X" -> "DTS-X"
			"TRUEHD" -> "Dolby TrueHD"
			"OPUS" -> "Opus"
			"VORBIS" -> "Vorbis"
			"MP3" -> "MP3"
			else -> codec?.uppercase().orEmpty()
		}

	private fun formatAudioLayout(audio: MediaStream): String {
		audio.channelLayout?.uppercase()?.let {
			return it.replace("_", " ")
		}

		return when (audio.channels) {
			1 -> "Mono"
			2 -> "Stereo"
			6 -> "5.1"
			8 -> "7.1"
			else -> "${audio.channels ?: ""}ch"
		}
	}
}
