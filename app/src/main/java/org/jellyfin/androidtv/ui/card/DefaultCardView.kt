@file:Suppress("DEPRECATION")

package org.jellyfin.androidtv.ui.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.ViewCardDefaultBinding
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.MenuBuilder
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.showIfNotEmpty
import kotlin.math.roundToInt

class DefaultCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), LifecycleObserver {

    init {
        isFocusable = true
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
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

        // Menu key should show the popup menu
        if (event.keyCode == KeyEvent.KEYCODE_MENU) return performLongClick()

        return false
    }

    private var currentScale: Float = 0.95f
    private var isFocused: Boolean = false

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateWhiteBorder(hasFocus: Boolean) {
        if (hasFocus) {
            if (foreground == null) {
                foreground = context.getDrawable(R.drawable.card_focused_border)
            }
        } else {
            foreground = null
        }
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
