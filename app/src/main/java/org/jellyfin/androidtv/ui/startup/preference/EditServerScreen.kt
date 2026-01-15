package org.jellyfin.androidtv.ui.startup.preference.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.ui.preference.screen.Colors
import org.jellyfin.androidtv.ui.preference.screen.PreferenceCard
import org.jellyfin.androidtv.ui.preference.screen.PreferenceHeader
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun EditServerScreenCompose(
    serverId: UUID,
    startupViewModel: StartupViewModel,
    serverUserRepository: ServerUserRepository,
    onBack: () -> Unit = {},
    onNavigateToEditUser: (UUID, UUID) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firstItemFocusRequester = remember { FocusRequester() }

    // State for server and users
    var server by remember { mutableStateOf(startupViewModel.getServer(serverId)) }
    var users by remember { mutableStateOf(emptyList<PrivateUser>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Load users and observe changes
    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue with screen initialization
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                server?.let { currentServer ->
                    users = serverUserRepository.getStoredServerUsers(currentServer)
                }
            }
        }
    }

    server?.let { currentServer ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PreferenceHeader(context.getString(R.string.pref_accounts))
            }

            if (users.isNotEmpty()) {
                items(users) { user ->
                    val lastUsedDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(user.lastUsed),
                        ZoneId.systemDefault()
                    )
                    val lastUsedDescription = context.getString(
                        R.string.lbl_user_last_used,
                        lastUsedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        lastUsedDate.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    )

                    PreferenceCard(
                        title = user.name,
                        description = lastUsedDescription,
                        icon = R.drawable.ic_user,
                        onClick = { onNavigateToEditUser(currentServer.id, user.id) },
                        modifier = if (user == users.first()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    )
                }
            } else {
                item {
                    Text(
                        text = context.getString(R.string.msg_no_users_found),
                        color = Colors.OnSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            item {
                PreferenceHeader(context.getString(R.string.lbl_server))
            }

            item {
                PreferenceCard(
                    title = context.getString(R.string.lbl_remove_server),
                    description = context.getString(R.string.lbl_remove_users),
                    icon = R.drawable.ic_delete,
                    onClick = { showDeleteDialog = true }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = context.getString(R.string.lbl_remove_server),
                    color = Colors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            containerColor = Colors.PanelBackground,
            shape = RoundedCornerShape(12.dp),
            text = {
                Text(
                    text = context.getString(R.string.msg_remove_server_confirm),
                    color = Colors.OnSurface,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    startupViewModel.deleteServer(serverId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text(
                        text = context.getString(R.string.lbl_remove),
                        color = Colors.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = context.getString(android.R.string.cancel),
                        color = Colors.OnSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}
