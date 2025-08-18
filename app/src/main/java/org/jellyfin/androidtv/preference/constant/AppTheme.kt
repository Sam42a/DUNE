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
	 * The default OLED Dark theme based on https://github.com/LitCastVlog/jellyfin-androidtv-OLED fork theme
	 */
	DARK(R.string.pref_theme_dark),

	/**
	 * The "classic" emerald theme enhanced
	 */
	EMERALD(R.string.pref_theme_emerald),

	/**
	 *  A minimal theme optimized and Inspired by the ElegantFin theme for the web
	 */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),

	/**
	 * A minimal Dark theme optimized for low-end devices with basic colors and reduced animations
	 */
	BASIC(R.string.pref_theme_basic),

	/**
	 * A Netflix-inspired theme with a dark background and red accents
	 */
	FLEXY(R.string.pref_theme_flexy),

	/**
	 * A warm yellow/gold theme with dark background
	 */
	YELLOW_TOWN(R.string.pref_theme_yellow_town),

	/**
	 * A Dark purple theme with dark background
	 */
	DARK_PURPLE(R.string.pref_theme_dark_purple)
}

