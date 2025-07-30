package org.jellyfin.androidtv.ui.preference.category

import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.screen.LicensesScreen
import org.jellyfin.androidtv.util.AppUpdater
import org.jellyfin.androidtv.util.UpdateResult
import timber.log.Timber

private const val CURRENT_VERSION = "0.0.7"

fun OptionsScreen.aboutCategory() = category {
    setTitle(R.string.pref_about_title)

    link {
        title = "Dune app version"
        content = CURRENT_VERSION
        icon = R.drawable.dune_icon
    }

    action {
        title = "Check for updates"
        icon = R.drawable.ic_check_update
        onActivate = {
            checkForUpdates(context)
        }
    }

    link {
        setTitle(R.string.pref_device_model)
        content = "${Build.MANUFACTURER} ${Build.MODEL}"
        icon = R.drawable.ic_device
    }

    link {
        setTitle(R.string.licenses_link)
        setContent(R.string.licenses_link_description)
        icon = R.drawable.ic_license
        withFragment<LicensesScreen>()
    }
}

private fun checkForUpdates(context: Context) {
    val appUpdater = AppUpdater(context)

    // Show checking message
    Toast.makeText(context, context.getString(R.string.update_checking), Toast.LENGTH_SHORT).show()

    CoroutineScope(Dispatchers.Main).launch {
        val result = withContext(Dispatchers.IO) {
            try {
                appUpdater.checkForUpdates(CURRENT_VERSION)
            } catch (e: Exception) {
                UpdateResult.Error(e.message ?: "Unknown error")
            }
        }

        when (result) {
            is UpdateResult.UpdateAvailable -> {
                // Show update available message
                Toast.makeText(
                    context,
                    context.getString(R.string.update_available_message, result.version),
                    Toast.LENGTH_LONG
                ).show()

                // Start download and install in a coroutine
                Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
				Timber.tag("UpdateCheck").d("Starting download for version ${result.version}")
                CoroutineScope(Dispatchers.Main).launch {
                    try {
						Timber.tag("UpdateCheck").d("Launching download coroutine")
                        withContext(Dispatchers.IO) {
                            try {
								Timber.tag("UpdateCheck").d("Calling downloadAndInstall")
                                appUpdater.downloadAndInstall(result.version, result.downloadUrl)
								Timber.tag("UpdateCheck").d("downloadAndInstall completed")
                            } catch (e: Exception) {
								Timber.tag("UpdateCheck").e(e, "Error in downloadAndInstall")
                                throw e
                            }
                        }
                    } catch (e: Exception) {
						Timber.tag("UpdateCheck").e(e, "Error in coroutine")
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.update_download_failed)}: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            is UpdateResult.NoUpdateAvailable -> {
                Toast.makeText(
                    context,
                    R.string.update_no_updates,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is UpdateResult.Error -> {
                Toast.makeText(
                    context,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
