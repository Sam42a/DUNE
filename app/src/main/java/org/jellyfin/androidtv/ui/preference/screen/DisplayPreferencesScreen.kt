package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository

@Composable
fun DisplayPreferencesScreenCompose(
    preferencesId: String,
    allowViewSelection: Boolean = true,
    onBack: () -> Unit = {},
    preferencesRepository: PreferencesRepository
) {
    val context = LocalContext.current
    val libraryPreferences = preferencesRepository.getLibraryPreferences(preferencesId)

    // Track if preferences have been modified
    var preferencesModified by remember { mutableStateOf(false) }

    // Set result when preferences are modified
    LaunchedEffect(preferencesModified) {
        if (preferencesModified) {
            // Get the current activity and set result
            (context as? android.app.Activity)?.let { activity ->
                activity.setResult(android.app.Activity.RESULT_OK)
            }
        }
    }

    val firstItemFocusRequester = remember { FocusRequester() }
    var shouldRequestFocus by remember { mutableStateOf(false) }

    // Request focus when the first item should be focused
    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            firstItemFocusRequester.requestFocus()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PreferenceHeader(context.getString(R.string.lbl_display_preferences))
        }

        item {
            val (posterSize, setPosterSize) = rememberEnumPreferenceState(
                preference = LibraryPreferences.posterSize,
                preferences = libraryPreferences
            )
            EnumPreference(
                title = context.getString(R.string.lbl_image_size),
                value = posterSize,
                onValueChange = { newValue ->
                    setPosterSize(newValue)
                    preferencesModified = true
                },
                options = PosterSize.entries,
                optionLabel = { context.getString(it.nameRes) },
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )

            // Trigger focus request after first item is composed
            LaunchedEffect(Unit) {
                shouldRequestFocus = true
            }
        }

        item {
            val (imageType, setImageType) = rememberEnumPreferenceState(
                preference = LibraryPreferences.imageType,
                preferences = libraryPreferences
            )
            EnumPreference(
                title = context.getString(R.string.lbl_image_type),
                value = imageType,
                onValueChange = { newValue ->
                    setImageType(newValue)
                    preferencesModified = true
                },
                options = ImageType.entries,
                optionLabel = { context.getString(it.nameRes) }
            )
        }

        item {
            val (gridDirection, setGridDirection) = rememberEnumPreferenceState(
                preference = LibraryPreferences.gridDirection,
                preferences = libraryPreferences
            )
            EnumPreference(
                title = context.getString(R.string.grid_direction),
                value = gridDirection,
                onValueChange = { newValue ->
                    setGridDirection(newValue)
                    preferencesModified = true
                },
                options = GridDirection.entries,
                optionLabel = { context.getString(it.nameRes) }
            )
        }

        item {
            val (showItemTitlesOnFocus, setShowItemTitlesOnFocus) = rememberPreferenceState(
                preference = LibraryPreferences.showItemTitlesOnFocus,
                preferences = libraryPreferences
            )
            SwitchPreference(
                title = context.getString(R.string.show_item_titles_on_focus),
                description = context.getString(R.string.show_item_titles_on_focus_description),
                checked = showItemTitlesOnFocus,
                preference = LibraryPreferences.showItemTitlesOnFocus,
                onCheckedChange = {
                    setShowItemTitlesOnFocus(it)
                    preferencesModified = true
                }
            )
        }

        if (allowViewSelection) {
            item {
                val (enableSmartScreen, setEnableSmartScreen) = rememberPreferenceState(
                    preference = LibraryPreferences.enableSmartScreen,
                    preferences = libraryPreferences
                )
                SwitchPreference(
                    title = context.getString(R.string.enable_smart_view),
                    description = context.getString(R.string.enable_smart_view_description),
                    checked = enableSmartScreen,
                    preference = LibraryPreferences.enableSmartScreen,
                    onCheckedChange = {
                        setEnableSmartScreen(it)
                        preferencesModified = true
                    }
                )
            }
        }
    }
}
