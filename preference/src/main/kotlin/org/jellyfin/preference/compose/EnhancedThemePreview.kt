package org.jellyfin.preference.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Jellyfin Enhanced color palette based on Jellyfin's dark theme
 */
private val JellyfineDarkColors = darkColors(
    primary = Color(0xFF00A4DC),        // Jellyfin blue
    primaryVariant = Color(0xFF0085B2), // Darker blue
    secondary = Color(0xFF52B54B),      // Green accent
    background = Color(0xFF101010),     // Nearly black
    surface = Color(0xFF202020),        // Dark gray
    error = Color(0xFFCF6679),          // Error red
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)

/**
 * Preview wrapper that applies the Jellyfin Enhanced theme
 */
@Composable
fun JellyfineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = JellyfineDarkColors,
        content = content
    )
}

/**
 * Example preview with the Jellyfin Enhanced theme
 */
@Preview(showBackground = true)
@Composable
fun EnhancedThemePreferenceScreenPreview() {
    JellyfineTheme {
        Surface(color = MaterialTheme.colors.background) {
            PreferenceScreen(title = "Jellyfine Preferences") {
                PreferenceCategory(title = "Enhanced Features") {
                    SwitchPreference(
                        title = "Enhanced Playback",
                        description = "Enable enhanced playback features",
                        checked = true,
                        onCheckedChange = {}
                    )
                    
                    CheckboxPreference(
                        title = "Enhanced UI",
                        description = "Enable enhanced user interface elements",
                        checked = true,
                        onCheckedChange = {}
                    )
                }
                
                // Online Subtitles - based on your implementation
                PreferenceCategory(title = "Online Subtitles") {
                    SwitchPreference(
                        title = "Enable Online Subtitles",
                        description = "Search and download subtitles from online sources",
                        checked = true,
                        onCheckedChange = {}
                    )
                    
                    PreferenceItem(
                        title = "OpenSubtitles API Key",
                        description = "Enter your OpenSubtitles API key"
                    )
                    
                    PreferenceItem(
                        title = "Subdl Integration",
                        description = "Configure Subdl subtitle provider settings"
                    )
                }
            }
        }
    }
}
