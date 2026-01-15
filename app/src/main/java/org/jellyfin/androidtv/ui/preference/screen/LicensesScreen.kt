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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import org.jellyfin.androidtv.R

@Composable
fun LicensesScreenCompose(
    libraryId: String? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val libs = remember { Libs.Builder().withContext(context).build() }

    if (libraryId != null) {
        // Show individual library details
        val library = libs.libraries.find { it.artifactId == libraryId }
        if (library != null) {
            LibraryDetailsScreenCompose(
                library = library,
                onBack = onBack
            )
        } else {
            // Library not found, show list
            LicensesListScreenCompose(
                libs = libs,
                onBack = onBack
            )
        }
    } else {
        // Show library list
        LicensesListScreenCompose(
            libs = libs,
            onBack = onBack
        )
    }
}

@Composable
fun LicensesListScreenCompose(
    libs: Libs,
    onBack: () -> Unit = {}
) {
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
            PreferenceHeader(LocalContext.current.getString(R.string.open_source_licenses))
        }

        val sortedLibraries = libs.libraries.sortedBy { it.name.lowercase() }
        items(sortedLibraries) { library ->
            val isFirstLibrary = library == sortedLibraries.firstOrNull()
            PreferenceCard(
                title = "${library.name} ${library.artifactVersion}",
                description = library.licenses.joinToString(", ") { it.name },
                icon = R.drawable.ic_license,
                onClick = {
                    // Navigate to library details - this would need navigation implementation
                    // For now, we'll just show the details in the same screen
                },
                modifier = if (isFirstLibrary) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            )
        }
    }
}

@Composable
fun LibraryDetailsScreenCompose(
    library: Library,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val details = mutableListOf<Pair<String, String>>()

    library.description?.let { details.add(context.getString(R.string.license_description) to it) }
    details.add(context.getString(R.string.license_version) to (library.artifactVersion ?: "Unknown"))
    details.add(context.getString(R.string.license_artifact) to (library.artifactId ?: "Unknown"))
    library.website?.let { details.add(context.getString(R.string.license_website) to it) }
    library.scm?.url?.let { details.add(context.getString(R.string.license_repository) to it) }
    library.developers.forEach { developer ->
        details.add(context.getString(R.string.license_author) to (developer.name ?: "Unknown"))
    }
    library.licenses.forEach { license ->
        details.add(context.getString(R.string.license_license) to (license.name ?: "Unknown"))
    }

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
            PreferenceHeader(library.name)
        }

        items(details) { (key, value) ->
            val isFirstDetail = details.indexOfFirst { it.first == key } == 0
            PreferenceCard(
                title = value,
                description = key,
                icon = R.drawable.ic_crash,
                onClick = { },
                modifier = if (isFirstDetail) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            )
        }
    }
}
