package org.jellyfin.playback.core.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import org.jellyfin.playback.core.PlaybackManager

/**
 * A view that is used to display the subtitle output of the playing media.
 * The [playbackManager] must be set when the view is initialized.
 */
class PlayerSubtitleView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
	lateinit var playbackManager: PlaybackManager

	private var pendingVerticalOffsetPercent: Int? = null

	fun setVerticalOffset(percent: Int) {
		if (height == 0) {
			pendingVerticalOffsetPercent = percent
			return
		}
		val offsetPx = (height * percent / 100f)
		translationY = offsetPx
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (!isInEditMode) {
			playbackManager.backendService.attachSubtitleView(this)
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		pendingVerticalOffsetPercent?.let {
			setVerticalOffset(it)
			pendingVerticalOffsetPercent = null
		}
	}
}
