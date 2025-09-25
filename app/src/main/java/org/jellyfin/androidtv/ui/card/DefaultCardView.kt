@file:Suppress("DEPRECATION")

package org.jellyfin.androidtv.ui.card

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.ViewCardDefaultBinding
import org.jellyfin.androidtv.util.MenuBuilder
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.showIfNotEmpty
import timber.log.Timber
import kotlin.math.roundToInt

class DefaultCardView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), LifecycleObserver {
	private val borderView = View(context).apply {
		val drawable = GradientDrawable()
		drawable.shape = GradientDrawable.RECTANGLE
		drawable.setStroke(
			8, // width in pixels
			Color.RED // color
		)
		drawable.cornerRadius = 16f // radius in pixels
		background = drawable
		bringToFront()
		visibility = View.INVISIBLE
	}

	init {
		stateListAnimator = null
		elevation = 0f
		outlineProvider = null // Remove any outline that might cause elevation
		setBackgroundResource(0) // Remove any background that might have elevation
	}

	override fun setElevation(elevation: Float) {
		super.setElevation(0f) // Always set elevation to 0
	}

	init {
		isFocusable = true
		descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false

		val params = LayoutParams(
			LayoutParams.MATCH_PARENT,
			LayoutParams.MATCH_PARENT
		)
		// Add some margin to ensure the border is visible
		val margin = resources.getDimensionPixelSize(R.dimen.card_border_padding)
		params.setMargins(-margin, -margin, -margin, -margin)

		addView(borderView, params)

		setClipChildren(false)
		setClipToPadding(false)

	}

	val binding = ViewCardDefaultBinding.inflate(LayoutInflater.from(context), this, true)

	fun setSize(size: Size) = when (size) {
		Size.SQUARE -> setSize(size.width, size.height)
		Size.SQUARE_SMALL -> setSize(size.width, size.height)
	}

	private fun setSize(newWidth: Int, newHeight: Int) {
		binding.bannerContainer.updateLayoutParams {
			@Suppress("MagicNumber")
			height = (newHeight * context.resources.displayMetrics.density + 0.5f).roundToInt()
		}

		val horizontalPadding = with(binding.container) { paddingStart + paddingEnd }
		binding.container.updateLayoutParams {
			@Suppress("MagicNumber")
			width = (newWidth * context.resources.displayMetrics.density + 0.5f).roundToInt() + horizontalPadding
		}

		invalidate()
	}

	fun setImage(
		url: String? = null,
		blurHash: String? = null,
		placeholder: Drawable? = null,
	) = binding.banner.load(url, blurHash, placeholder)

	fun setPopupMenu(init: MenuBuilder.() -> Unit) {
		setOnLongClickListener {
			popupMenu(context, binding.root, init = init).showIfNotEmpty()
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (super.onKeyUp(keyCode, event)) return true

		if (event.keyCode == KeyEvent.KEYCODE_MENU) return performLongClick()

		return false
	}

	private var currentScale: Float = 0.95f
	private var isFocused: Boolean = false

	private fun updateWhiteBorder(hasFocus: Boolean) {
		// Always use the border view for now
		if (hasFocus) {
			borderView.visibility = View.VISIBLE
			borderView.bringToFront()
		} else {
			borderView.visibility = View.INVISIBLE
		}

		// Debug: Print focus state
		Timber.tag("DefaultCardView").d("Focus changed: $hasFocus, visibility: ${borderView.visibility}")

		// Force redraw
		invalidate()
		requestLayout()
	}

	override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

		// Skip if focus state hasn't changed
		if (isFocused == gainFocus) return
		isFocused = gainFocus

		// Cancel any ongoing animations
		animate().cancel()

		// Update scale
		val targetScale = if (gainFocus) 1.0f else 0.95f
		if (currentScale != targetScale) {
			currentScale = targetScale
			scaleX = targetScale
			scaleY = targetScale
		}

		// Update white border
		updateWhiteBorder(gainFocus)
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
	fun onResume() {
		// Update border state when preferences might have changed
		updateWhiteBorder(hasFocus())
	}

	@Suppress("MagicNumber")
	enum class Size(val width: Int, val height: Int) {
		SQUARE(110, 110),
		SQUARE_SMALL(99, 99) // 10% smaller
	}
}
