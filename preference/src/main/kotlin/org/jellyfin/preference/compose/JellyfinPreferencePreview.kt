package org.jellyfin.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Example preview for the authentication preferences screen
 */
@Preview(showBackground = true)
@Composable
fun AuthenticationPreferencePreview() {
    MaterialTheme {
        PreferenceScreen(title = "Authentication") {
            // Auto Login Section
            PreferenceCategory(title = "Auto Sign In") {
                DropdownPreference(
                    title = "Auto Login User Behavior",
                    description = "Choose how auto login should behave",
                    options = mapOf(
                        "LAST_USER" to "Last User",
                        "SPECIFIC_USER" to "Specific User"
                    ),
                    selectedOption = "LAST_USER",
                    onOptionSelected = {}
                )
                
                SwitchPreference(
                    title = "Always Authenticate",
                    description = "Require authentication every time the app is opened",
                    checked = false,
                    onCheckedChange = {}
                )
            }
            
            // Server Management
            PreferenceCategory(title = "Manage Servers") {
                PreferenceItem(
                    title = "My Jellyfin Server",
                    description = "https://jellyfin.example.com"
                )
                
                PreferenceItem(
                    title = "Local Server",
                    description = "192.168.1.100:8096"
                )
            }
        }
    }
}

/**
 * Example preview for the online subtitles preference screen
 */
@Preview(showBackground = true)
@Composable
fun OnlineSubtitlesPreferencePreview() {
    MaterialTheme {
        PreferenceScreen(title = "Online Subtitles") {
            PreferenceCategory(title = "OpenSubtitles") {
                PreferenceItem(
                    title = "Username",
                    description = "Your OpenSubtitles username"
                )
                
                PreferenceItem(
                    title = "Password",
                    description = "Your OpenSubtitles password (stored securely)"
                )
                
                CheckboxPreference(
                    title = "Use OpenSubtitles",
                    description = "Search for subtitles on OpenSubtitles",
                    checked = true,
                    onCheckedChange = {}
                )
            }
            
            PreferenceCategory(title = "Subdl") {
                CheckboxPreference(
                    title = "Use Subdl",
                    description = "Search for subtitles on Subdl",
                    checked = false,
                    onCheckedChange = {}
                )
            }
            
            PreferenceCategory(title = "Download Settings") {
                SwitchPreference(
                    title = "Auto-download Subtitles",
                    description = "Automatically download subtitles for new media",
                    checked = true,
                    onCheckedChange = {}
                )
                
                DropdownPreference(
                    title = "Preferred Language",
                    description = "Choose your preferred subtitle language",
                    options = mapOf(
                        "en" to "English",
                        "fr" to "French",
                        "es" to "Spanish",
                        "de" to "German"
                    ),
                    selectedOption = "en",
                    onOptionSelected = {}
                )
            }
        }
    }
}

/**
 * Example preview for the playback preferences screen
 */
@Preview(showBackground = true)
@Composable
fun PlaybackPreferencePreview() {
    MaterialTheme {
        PreferenceScreen(title = "Playback") {
            PreferenceCategory(title = "Video") {
                CheckboxPreference(
                    title = "Auto-play Next Episode",
                    description = "Automatically play the next episode when the current one finishes",
                    checked = true,
                    onCheckedChange = {}
                )
                
                DropdownPreference(
                    title = "Default Quality",
                    description = "Choose default streaming quality",
                    options = mapOf(
                        "auto" to "Auto",
                        "1080p" to "1080p",
                        "720p" to "720p",
                        "480p" to "480p"
                    ),
                    selectedOption = "auto",
                    onOptionSelected = {}
                )
            }
            
            PreferenceCategory(title = "Audio") {
                SwitchPreference(
                    title = "Normalize Audio",
                    description = "Adjust volume levels to be consistent across different content",
                    checked = false,
                    onCheckedChange = {}
                )
            }
        }
    }
}
