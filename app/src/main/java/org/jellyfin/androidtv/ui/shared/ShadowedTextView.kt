package org.jellyfin.androidtv.ui.shared

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class ShadowedTextView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

	init {
		// Shadow due to light backgrounds
		setShadowLayer(6f, 3f, 3f, Color.BLACK)
	}
}
