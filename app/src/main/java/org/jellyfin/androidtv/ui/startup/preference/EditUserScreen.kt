package org.jellyfin.androidtv.ui.startup.preference.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.ui.preference.screen.Colors
import org.jellyfin.androidtv.ui.preference.screen.PreferenceCard
import org.jellyfin.androidtv.ui.preference.screen.PreferenceHeader
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import java.util.UUID

@Composable
fun EditUserScreenCompose(
    serverId: UUID,
    userId: UUID,
    startupViewModel: StartupViewModel,
    authenticationRepository: AuthenticationRepository,
    serverUserRepository: ServerUserRepository,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    // Request focus on first item when screen loads
    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue with screen initialization
        }
    }

    // State for user and server
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val server = remember { startupViewModel.getServer(serverId) }
    val user = remember {
        server?.let {
            serverUserRepository.getStoredServerUsers(it).find { user -> user.id == userId }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.lbl_user_actions))
        }

        user?.let { currentUser ->
            // Sign Out Action (only if user has access token)
            if (currentUser.accessToken != null) {
                item {
                    PreferenceCard(
                        title = context.getString(R.string.lbl_sign_out),
                        description = context.getString(R.string.lbl_sign_out_content),
                        icon = R.drawable.ic_logout,
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.focusRequester(firstItemFocusRequester)
                    )
                }
            }

            // Remove User Action
            item {
                PreferenceCard(
                    title = context.getString(R.string.lbl_remove),
                    description = context.getString(R.string.lbl_remove_user_content),
                    icon = R.drawable.ic_delete,
                    onClick = { showRemoveDialog = true }
                )
            }
        } ?: run {
            item {
                Text(
                    text = context.getString(R.string.lbl_user_not_found),
                    color = Colors.OnSurfaceVariant,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }

    // Sign Out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = context.getString(R.string.lbl_sign_out),
                    color = Colors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            containerColor = Colors.PanelBackground,
            shape = RoundedCornerShape(12.dp),
            text = {
                Text(
                    text = context.getString(R.string.msg_sign_out_confirm),
                    color = Colors.OnSurface,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    user?.let { authenticationRepository.logout(it) }
                    showSignOutDialog = false
                }) {
                    Text(
                        text = context.getString(R.string.lbl_sign_out),
                        color = Colors.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
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

    // Remove confirmation dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text(
                    text = context.getString(R.string.lbl_remove),
                    color = Colors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            containerColor = Colors.PanelBackground,
            shape = RoundedCornerShape(12.dp),
            text = {
                Text(
                    text = context.getString(R.string.msg_remove_user_confirm),
                    color = Colors.OnSurface,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    user?.let { serverUserRepository.deleteStoredUser(it) }
                    showRemoveDialog = false
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
                TextButton(onClick = { showRemoveDialog = false }) {
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
