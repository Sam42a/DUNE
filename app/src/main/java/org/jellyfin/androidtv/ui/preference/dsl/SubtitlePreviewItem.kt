package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.jellyfin.androidtv.R

import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.TypedValue
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat

import org.jellyfin.androidtv.preference.UserPreferences

class SubtitlePreviewItem(private val context: Context, private val userPreferences: UserPreferences) : OptionsItem {
    override fun build(category: PreferenceCategory, container: OptionsUpdateFunContainer) {
        val pref = object : Preference(context) {
            init {
                layoutResource = R.layout.subtitle_preview
            }
            fun updatePreview() {
                notifyChanged()
            }
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                val textView = holder.itemView.findViewById<TextView>(R.id.subtitlePreviewText)
                textView?.let {
                    it.text = "Subtitle Preview Example"
                    // Color
                    val color = userPreferences[UserPreferences.subtitlesTextColor].toInt()
                    it.setTextColor(color)
                    // Background
                    val bgColor = userPreferences[UserPreferences.subtitlesBackgroundColor].toInt()
                    it.setBackgroundColor(bgColor)
                    // Size
                    val size = userPreferences[UserPreferences.subtitlesTextSize] * 24f
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                    // Weight
                    val weight = userPreferences[UserPreferences.subtitlesTextWeightValue]
                    it.setTypeface(null, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                    // Stroke (outline) -- not natively supported, skip for now
                    
                }
            }
        }
        pref.isSelectable = false
        // Register a callback so the preview updates when any preference changes
        container += { pref.updatePreview() }
        category.addPreference(pref)
    }
}

@OptionsDSL
fun OptionsCategory.subtitlePreview(init: SubtitlePreviewItem.() -> Unit = {}) {
    val userPreferences = org.koin.java.KoinJavaComponent.getKoin().get<UserPreferences>()
    this += SubtitlePreviewItem(context, userPreferences).apply(init)
}
