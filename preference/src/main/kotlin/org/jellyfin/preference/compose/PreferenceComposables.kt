package org.jellyfin.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A composable that represents a preference screen
 */
@Composable
fun PreferenceScreen(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

/**
 * A composable that represents a preference category
 */
@Composable
fun PreferenceCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * A composable that represents a basic preference item
 */
@Composable
fun PreferenceItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.body2
                )
            }
        }
        
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
    Divider()
}

/**
 * A composable that represents a checkbox preference
 */
@Composable
fun CheckboxPreference(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceItem(
        title = title,
        description = description,
        trailing = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

/**
 * A composable that represents a switch preference
 */
@Composable
fun SwitchPreference(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceItem(
        title = title,
        description = description,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

/**
 * A composable that represents a dropdown preference
 */
@Composable
fun <T> DropdownPreference(
    title: String,
    description: String? = null,
    options: Map<T, String>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    PreferenceItem(
        title = title,
        description = description,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = options[selectedOption] ?: "",
                    style = MaterialTheme.typography.body1
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Show options"
                )
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (option, label) ->
                        DropdownMenuItem(
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        ) {
                            Text(text = label)
                        }
                    }
                }
            }
        },
        icon = null
    )
}

// Preview samples

@Preview(showBackground = true)
@Composable
fun PreferenceScreenPreview() {
    MaterialTheme {
        PreferenceScreen(
            title = "Preferences"
        ) {
            PreferenceCategory(
                title = "General"
            ) {
                SwitchPreference(
                    title = "Enable notifications",
                    description = "Show notifications for new messages",
                    checked = true,
                    onCheckedChange = {}
                )
                
                CheckboxPreference(
                    title = "Auto play",
                    description = "Automatically play videos when selected",
                    checked = false,
                    onCheckedChange = {}
                )
            }
            
            PreferenceCategory(
                title = "Display"
            ) {
                PreferenceItem(
                    title = "Theme",
                    description = "Change the app theme",
                    icon = Icons.Filled.Settings
                )
                
                val options = mapOf(
                    "auto" to "Auto (system default)",
                    "light" to "Light mode",
                    "dark" to "Dark mode"
                )
                
                DropdownPreference(
                    title = "Dark mode",
                    description = "Control when dark mode is used",
                    options = options,
                    selectedOption = "auto",
                    onOptionSelected = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreferenceCategoryPreview() {
    MaterialTheme {
        PreferenceCategory(
            title = "Authentication"
        ) {
            SwitchPreference(
                title = "Always authenticate",
                description = "Require authentication every time the app is opened",
                checked = true,
                onCheckedChange = {}
            )
            
            CheckboxPreference(
                title = "Remember credentials",
                description = "Store login information",
                checked = false,
                onCheckedChange = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreferenceItemPreview() {
    MaterialTheme {
        Column {
            PreferenceItem(
                title = "Server address",
                description = "The URL of your Jellyfin server",
                icon = Icons.Filled.Settings
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckboxPreferencePreview() {
    MaterialTheme {
        CheckboxPreference(
            title = "Auto-connect on startup",
            description = "Automatically connect to the server when the app starts",
            checked = true,
            onCheckedChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SwitchPreferencePreview() {
    MaterialTheme {
        SwitchPreference(
            title = "Enable subtitles",
            description = "Show subtitles when available",
            checked = false,
            onCheckedChange = {}
        )
    }
}
