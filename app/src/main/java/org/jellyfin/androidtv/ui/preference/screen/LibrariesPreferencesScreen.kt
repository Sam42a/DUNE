package org.jellyfin.androidtv.ui.preference.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType

@Composable
fun LibrariesPreferencesScreenCompose(
    onBack: () -> Unit = {},
    onNavigateToDisplayPreferences: (String, Boolean) -> Unit = { _, _ -> },
    userViewsRepository: UserViewsRepository
) {
    val context = LocalContext.current

    val firstItemFocusRequester = remember { FocusRequester() }

    // Collect user views (libraries)
    val userViews = userViewsRepository.views.collectAsStateWithLifecycle(
        initialValue = emptyList<BaseItemDto>()
    )

    // Delay focus request until after items are composed
    LaunchedEffect(userViews.value) {
        if (userViews.value.isNotEmpty()) {
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
            PreferenceHeader(context.getString(R.string.pref_libraries))
        }

        val sortedLibraries = userViews.value.sortedBy { it.name?.lowercase() ?: "" }

        items(sortedLibraries) { library ->
            val allowViewSelection = userViewsRepository.allowGridView(library.collectionType)
            val isFirstLibrary = library == sortedLibraries.firstOrNull()

            PreferenceCard(
                title = library.name ?: context.getString(R.string.lbl_library),
                description = "",
                icon = when (library.collectionType) {
                    CollectionType.MOVIES -> R.drawable.ic_movie
                    CollectionType.TVSHOWS -> R.drawable.ic_tv
                    CollectionType.MUSIC -> R.drawable.ic_select_audio
                    CollectionType.LIVETV -> R.drawable.ic_guide
                    else -> R.drawable.ic_folder
                },
                onClick = {
                    if (allowViewSelection) {
                        library.displayPreferencesId?.let { preferencesId ->
                            // Debug: Log the preferencesId
                            android.util.Log.d("LibrariesPrefs", "Navigating to DisplayPreferencesCompose with ID: $preferencesId")
                            // Navigate to Compose DisplayPreferencesScreen with result handling
                            val intent = android.content.Intent(context, org.jellyfin.androidtv.ui.preference.PreferencesComposeActivity::class.java).apply {
                                putExtra("displayPreferencesId", preferencesId)
                                putExtra("allowViewSelection", true)
                                putExtra("initialScreen", "display_preferences")
                                putExtra("standalone", true)
                                putExtra("shouldRefresh", true)
                            }
                            // Use startActivityForResult to get result for refresh
                            (context as? androidx.appcompat.app.AppCompatActivity)?.startActivityForResult(intent, 1002)
                                ?: context.startActivity(intent)
                        } ?: run {
                            // Debug: Log when preferencesId is null
                            android.util.Log.d("LibrariesPrefs", "Library ${library.name} has null displayPreferencesId")
                        }
                    } else if (library.collectionType == CollectionType.LIVETV) {
                        // Navigate to Live TV Guide options
                        // This would need to be implemented
                    }
                    // If not allowed, do nothing (card will be disabled)
                },
                enabled = allowViewSelection || library.collectionType == CollectionType.LIVETV,
                modifier = if (isFirstLibrary) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            )
        }
    }
}
