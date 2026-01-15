package org.jellyfin.androidtv.ui.preference.screen

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.AppUpdater
import org.jellyfin.androidtv.util.UpdateResult
import timber.log.Timber

private const val CURRENT_VERSION = "0.1.1"

@Composable
fun AboutScreenCompose(
    onNavigateToLicenses: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.pref_about_title))
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.app_version),
                description = CURRENT_VERSION,
                icon = R.drawable.dune_logo,
                onClick = { },
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.Check_for_updates),
                description = context.getString(R.string.check_updates_description),
                icon = R.drawable.ic_check_update,
                onClick = {
                    checkForUpdates(context)
                }
            )
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.pref_device_model),
                description = "${Build.MANUFACTURER} ${Build.MODEL}",
                icon = R.drawable.ic_device,
                onClick = { }
            )
        }

        item {
            PreferenceCard(
                title = context.getString(R.string.licenses_link),
                description = context.getString(R.string.licenses_link_description),
                icon = R.drawable.ic_license,
                onClick = {
                    onNavigateToLicenses()
                }
            )
        }
    }
}

private fun checkForUpdates(context: android.content.Context) {
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
                Toast.makeText(context, context.getString(R.string.update_download_starting), Toast.LENGTH_SHORT).show()
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
