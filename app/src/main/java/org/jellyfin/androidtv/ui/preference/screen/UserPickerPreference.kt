package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior
import java.text.DateFormat
import java.util.UUID

data class UserSelection(
    val behavior: UserSelectBehavior,
    val serverId: UUID?,
    val userId: UUID?,
)

@Composable
fun UserPickerPreference(
    title: String,
    value: UserSelection,
    onValueChange: (UserSelection) -> Unit,
    serverRepository: ServerRepository,
    serverUserRepository: ServerUserRepository,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowDisable: Boolean = true,
    allowLatest: Boolean = true
) {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    PreferenceCard(
        title = title,
        description = getUserSelectionDescription(context, value, serverRepository, serverUserRepository),
        icon = R.drawable.ic_user,
        onClick = { showDialog.value = true },
        modifier = modifier,
        enabled = enabled
    )

    if (showDialog.value) {
        UserSelectionDialog(
            title = title,
            currentValue = value,
            onValueSelected = { selected ->
                onValueChange(selected)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false },
            serverRepository = serverRepository,
            serverUserRepository = serverUserRepository,
            allowDisable = allowDisable,
            allowLatest = allowLatest
        )
    }
}

@Composable
private fun UserSelectionDialog(
    title: String,
    currentValue: UserSelection,
    onValueSelected: (UserSelection) -> Unit,
    onDismiss: () -> Unit,
    serverRepository: ServerRepository,
    serverUserRepository: ServerUserRepository,
    allowDisable: Boolean,
    allowLatest: Boolean
) {
    val context = LocalContext.current
    val firstOptionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstOptionFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, but continue with dialog initialization
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(420.dp)
            .height(480.dp),
        title = {
            Text(
                text = title,
                color = Colors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        containerColor = Colors.PanelBackground,
        shape = RoundedCornerShape(12.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Add special behaviors
                    if (allowDisable) {
                        item {
                            val selection = UserSelection(
                                behavior = UserSelectBehavior.DISABLED,
                                serverId = null,
                                userId = null
                            )
                            UserSelectionOption(
                                title = context.getString(R.string.user_picker_disable_title),
                                description = context.getString(R.string.user_picker_disable_summary),
                                isSelected = currentValue == selection,
                                isFirstOption = true,
                                focusRequester = firstOptionFocusRequester,
                                onClick = { onValueSelected(selection) }
                            )
                        }
                    }

                    if (allowLatest) {
                        item {
                            val selection = UserSelection(
                                behavior = UserSelectBehavior.LAST_USER,
                                serverId = null,
                                userId = null
                            )
                            UserSelectionOption(
                                title = context.getString(R.string.user_picker_last_user_title),
                                description = context.getString(R.string.user_picker_last_user_summary),
                                isSelected = currentValue == selection,
                                isFirstOption = !allowDisable,
                                focusRequester = if (!allowDisable) firstOptionFocusRequester else null,
                                onClick = { onValueSelected(selection) }
                            )
                        }
                    }

                    // Add users grouped by server
                    val servers = serverRepository.storedServers.value
                    servers.forEach { server ->
                        val users = serverUserRepository.getStoredServerUsers(server)
                        if (users.isNotEmpty()) {
                            item {
                                Text(
                                    text = server.name,
                                    color = Colors.OnSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            items(users) { user ->
                                val selection = UserSelection(
                                    behavior = UserSelectBehavior.SPECIFIC_USER,
                                    serverId = user.serverId,
                                    userId = user.id
                                )
                                UserSelectionOption(
                                    title = user.name,
                                    description = context.getString(
                                        R.string.lbl_user_last_used,
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(user.lastUsed),
                                        DateFormat.getTimeInstance(DateFormat.SHORT).format(user.lastUsed)
                                    ),
                                    isSelected = currentValue == selection,
                                    onClick = { onValueSelected(selection) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = context.getString(android.R.string.cancel),
                    color = Colors.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun UserSelectionOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFirstOption: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
                    keyEvent.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Colors.FocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(3.dp)
            )
            .background(
                when {
                    isFocused -> Colors.FocusedOverlay
                    isSelected -> Colors.Surface
                    else -> Color.Transparent
                },
                RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Colors.Primary,
                unselectedColor = Colors.OnSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Colors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = Colors.OnSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

private fun getUserSelectionDescription(
    context: android.content.Context,
    selection: UserSelection,
    serverRepository: ServerRepository,
    serverUserRepository: ServerUserRepository
): String {
    return when (selection.behavior) {
        UserSelectBehavior.DISABLED -> context.getString(R.string.user_picker_disable_summary)
        UserSelectBehavior.LAST_USER -> context.getString(R.string.user_picker_last_user_summary)
        UserSelectBehavior.SPECIFIC_USER -> {
            val server = selection.serverId?.let { serverRepository.storedServers.value.find { it.id == it } }
            val user = server?.let { serverUserRepository.getStoredServerUsers(it).find { user -> user.id == selection.userId } }
            user?.name ?: "Specific user"
        }
    }
}
