package org.jellyfin.androidtv.ui.preference.screen

import android.app.AlertDialog
import android.content.Intent
import android.text.format.Formatter
import coil3.ImageLoader
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.list
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.util.isTvDevice
import org.koin.android.ext.android.inject

class DeveloperPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()
	private val systemPreferences: SystemPreferences by inject()
	private val imageLoader: ImageLoader by inject()

	private fun showRestartDialog() {
		AlertDialog.Builder(requireContext())
			.setTitle(R.string.restart_required)
			.setMessage(R.string.restart_required_message)
			.setPositiveButton(R.string.restart_now) { _, _ ->
				val packageManager = requireContext().packageManager
				val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
				val mainIntent = Intent.makeRestartActivityTask(intent?.component)
				requireContext().startActivity(mainIntent)
				Runtime.getRuntime().exit(0)
			}
			.setNegativeButton(R.string.restart_later, null)
			.show()
	}

	override val screen by optionsScreen {
		setTitle(R.string.pref_developer_link)

		category {
			// Legacy debug flag
			// Not in use by much components anymore
			checkbox {
				setTitle(R.string.lbl_enable_debug)
				setContent(R.string.desc_debug)
				bind(userPreferences, UserPreferences.debuggingEnabled)
			}

			// UI Mode toggle
			if (!context.isTvDevice()) {
				checkbox {
					setTitle(R.string.disable_ui_mode_warning)
					bind(systemPreferences, SystemPreferences.disableUiModeWarning)
				}
			}

			// Only show in debug mode
			// some strings are hardcoded because these options don't show in beta/release builds
			if (BuildConfig.DEVELOPMENT) {
				checkbox {
					title = "Enable new playback module for video"
					setContent(R.string.enable_playback_module_description)

					bind(userPreferences, UserPreferences.playbackRewriteVideoEnabled)
				}
			}

			checkbox {
				setTitle(R.string.preference_enable_trickplay)
				setContent(R.string.enable_playback_module_description)

				bind(userPreferences, UserPreferences.trickPlayEnabled)
			}

			checkbox {
				setTitle(R.string.prefer_exoplayer_ffmpeg)
				setContent(R.string.prefer_exoplayer_ffmpeg_content)

				bind(userPreferences, UserPreferences.preferExoPlayerFfmpeg)
			}

			checkbox {
				setTitle(R.string.hardware_acceleration_enabled)
				setContent(R.string.hardware_acceleration_enabled_content)
				bind(userPreferences, UserPreferences.hardwareAccelerationEnabled)
			}

			action {
				setTitle(R.string.clear_image_cache)
				content = getString(R.string.clear_image_cache_content, Formatter.formatFileSize(context, imageLoader.diskCache?.size ?: 0))
				onActivate = {
					imageLoader.memoryCache?.clear()
					imageLoader.diskCache?.clear()
					rebuild()
				}
			}

			list {
				setTitle(R.string.pref_disk_cache_size)
				entries = setOf(
					0, 100, 250, 500, 800, 1024, 1536, 2048
				).associate {
					it.toString() to when (it) {
						0 -> getString(R.string.pref_disk_cache_size_disabled)
						100 -> getString(R.string.pref_disk_cache_size_100mb)
						250 -> getString(R.string.pref_disk_cache_size_250mb)
						500 -> getString(R.string.pref_disk_cache_size_500mb)
						800 -> getString(R.string.pref_disk_cache_size_800mb)
						1024 -> getString(R.string.pref_disk_cache_size_1gb)
						1536 -> getString(R.string.pref_disk_cache_size_1_5gb)
						2048 -> getString(R.string.pref_disk_cache_size_2gb)
						else -> it.toString()
					}
				}
				bind {
					get { userPreferences[UserPreferences.diskCacheSizeMb].toString() }
					set {
						val newValue = it.toInt()
						if (userPreferences[UserPreferences.diskCacheSizeMb] != newValue) {
							userPreferences[UserPreferences.diskCacheSizeMb] = newValue
							showRestartDialog()
						}
					}
					default { userPreferences.getDefaultValue(UserPreferences.diskCacheSizeMb).toString() }
				}
			}
		}
	}
}
