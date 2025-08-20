package org.jellyfin.androidtv.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Wrapper view for NowPlayingView to handle Compose view properly in XML layouts
 */
class NowPlayingWrapperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val nowPlayingView = NowPlayingView(context)

    init {
        addView(nowPlayingView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
}
