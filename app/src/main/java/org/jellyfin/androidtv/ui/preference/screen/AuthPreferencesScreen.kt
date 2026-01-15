package org.jellyfin.androidtv.ui.preference.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.AuthenticationSortBy
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.util.AppUpdater
import org.jellyfin.androidtv.util.UpdateResult
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

private const val CURRENT_VERSION = "0.1.1"


@Composable
fun AuthPreferencesScreenCompose(
    serverRepository: ServerRepository,
    serverUserRepository: ServerUserRepository,
    authenticationPreferences: AuthenticationPreferences,
    sessionRepository: SessionRepository,
    showAbout: Boolean = false,
    onNavigateToEditServer: (String) -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firstItemFocusRequester = remember { FocusRequester() }


    var storedServers by remember { mutableStateOf(serverRepository.storedServers.value) }

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                serverRepository.loadStoredServers()
                serverRepository.storedServers.collect { servers ->
                    storedServers = servers
                }
            }
        }
    }

    val (autoLoginBehavior, setAutoLoginBehavior) = rememberEnumPreferenceState(
        preference = AuthenticationPreferences.autoLoginUserBehavior,
        preferences = authenticationPreferences
    )

    val (autoLoginServerId, setAutoLoginServerId) = rememberPreferenceState(
        preference = AuthenticationPreferences.autoLoginServerId,
        preferences = authenticationPreferences
    )

    val (autoLoginUserId, setAutoLoginUserId) = rememberPreferenceState(
        preference = AuthenticationPreferences.autoLoginUserId,
        preferences = authenticationPreferences
    )

    val (sortBy, setSortBy) = rememberEnumPreferenceState(
        preference = AuthenticationPreferences.sortBy,
        preferences = authenticationPreferences
    )

    val (alwaysAuthenticate, setAlwaysAuthenticate) = rememberPreferenceState(
        preference = AuthenticationPreferences.alwaysAuthenticate,
        preferences = authenticationPreferences
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.pref_authentication_cat),)
        }

        if (!alwaysAuthenticate) {
            item {
                val currentUserSelection = UserSelection(
                    behavior = autoLoginBehavior,
                    serverId = autoLoginServerId.toUUIDOrNull(),
                    userId = autoLoginUserId.toUUIDOrNull()
                )

                UserPickerPreference(
                    title = context.getString(R.string.auto_sign_in),
                    value = currentUserSelection,
                    onValueChange = { selection ->
                        setAutoLoginBehavior(selection.behavior)
                        setAutoLoginServerId(selection.serverId?.toString() ?: "")
                        setAutoLoginUserId(selection.userId?.toString() ?: "")
                    },
                    serverRepository = serverRepository,
                    serverUserRepository = serverUserRepository,
                    modifier = Modifier.focusRequester(firstItemFocusRequester),
                    allowDisable = true,
                    allowLatest = true
                )
            }
        }

        item {
            EnumPreference(
                title = context.getString(R.string.sort_accounts_by),
                value = sortBy,
                onValueChange = setSortBy,
                options = AuthenticationSortBy.entries,
                description = context.getString(R.string.sort_accounts_by),
                optionLabel = { context.getString(it.nameRes) }
            )
        }

        if (storedServers.isNotEmpty()) {
            item {
                PreferenceHeader(context.getString(R.string.lbl_manage_servers))
            }

            items(storedServers) { server ->
                PreferenceCard(
                    title = server.name,
                    description = server.address,
                    icon = R.drawable.ic_house,
                    onClick = { onNavigateToEditServer(server.id.toString()) }
                )
            }
        }

        if (sessionRepository.currentSession.value != null) {
            item {
                PreferenceHeader(context.getString(R.string.advanced_settings))
            }

            item {
                SwitchPreference(
                    title = context.getString(R.string.always_authenticate),
                    description = context.getString(R.string.always_authenticate_description),
                    checked = alwaysAuthenticate,
                    preference = AuthenticationPreferences.alwaysAuthenticate,
                    onCheckedChange = setAlwaysAuthenticate
                )
            }
        }

        if (showAbout) {
            item {
                PreferenceHeader(context.getString(R.string.pref_about_title))
            }

            item {
                PreferenceCard(
					title = context.getString(R.string.app_version),
					description = CURRENT_VERSION,
					icon = R.drawable.dune_logo,
                    onClick = { }
                )
            }

            item {
                PreferenceCard(
					title = context.getString(R.string.Check_for_updates),
					description = context.getString(R.string.check_updates_description),
					icon = R.drawable.ic_check_update,
                    onClick = { checkForUpdates(context) }
                )
            }

            item {
                PreferenceCard(
                    title = context.getString(R.string.pref_device_model),
                    description = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    icon = R.drawable.ic_device,
                    onClick = { }
                )
            }

            item {
                PreferenceCard(
                    title = context.getString(R.string.licenses_link),
                    description = context.getString(R.string.licenses_link_description),
                    icon = R.drawable.ic_license,
                    onClick = { onNavigateToLicenses() }
                )
            }
        }
    }
}

private fun checkForUpdates(context: Context) {
    val appUpdater = AppUpdater(context)

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
                Toast.makeText(
                    context,
                    context.getString(R.string.update_available_message, result.version),
                    Toast.LENGTH_LONG
                ).show()
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
