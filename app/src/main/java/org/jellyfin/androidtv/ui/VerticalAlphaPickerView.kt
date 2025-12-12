package org.jellyfin.androidtv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.children
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.ViewButtonAlphaPickerBinding

class VerticalAlphaPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    var onAlphaSelected: (letter: Char) -> Unit = {}

    init {
        orientation = VERTICAL

        val letters = "#${resources.getString(R.string.byletter_letters)}"
        letters.forEach { letter ->
            val binding = ViewButtonAlphaPickerBinding.inflate(LayoutInflater.from(context), this, false)
            binding.button.apply {
                text = letter.toString()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 1, 0, 1)
                }
                setOnClickListener { _ ->
                    onAlphaSelected(letter)
                }
            }

            addView(binding.root)
        }
    }

    fun focus(letter: Char) {
        children
            .filterIsInstance<Button>()
            .firstOrNull { it.text == letter.toString() }
            ?.requestFocus()
    }
}
