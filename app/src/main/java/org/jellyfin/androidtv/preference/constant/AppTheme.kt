package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class AppTheme(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * The purple haze theme: deep blue and purple gradient
	 */
	PURPLE_HAZE(R.string.pref_theme_purple_haze),
	/**
	 * The default dark theme
	 */
	DARK(R.string.pref_theme_dark),

	/**
	 * OLED Dark theme based on https://github.com/LitCastVlog/jellyfin-androidtv-OLED fork theme
	 */
	EMERALD(R.string.pref_theme_emerald),

	/**
	 * The "classic" emerald theme enhanced
	 */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),

	/**
	 * A minimal theme optimized and based on the ElegantFin theme for the web
	 */
	BASIC(R.string.pref_theme_basic),

	/**
	 * A Netflix-inspired theme with a dark background and red accents
	 */
	FLEXY(R.string.pref_theme_flexy)
}
    /**
	 * A minimal Dark theme optimized for low-end devices with basic colors and reduced animations
     */
