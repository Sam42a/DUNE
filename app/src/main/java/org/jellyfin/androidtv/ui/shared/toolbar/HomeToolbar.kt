package org.jellyfin.androidtv.ui.shared.toolbar

import android.R.attr.scaleX
import android.R.attr.scaleY
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.BrowsingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun HomeToolbar(
    openSearch: () -> Unit,
    openLiveTv: () -> Unit,
    openSettings: () -> Unit,
    switchUsers: () -> Unit,
    openRandomMovie: (BaseItemDto) -> Unit = { _ -> },
    openLibrary: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    userSettingPreferences: UserSettingPreferences = koinInject(),
    userRepository: UserRepository = koinInject(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    // Get the button preferences
    val showLiveTvButton = userSettingPreferences.get(userSettingPreferences.showLiveTvButton)
    val showMasksButton = userSettingPreferences.get(userSettingPreferences.showRandomButton)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icons row
        Row(
            modifier = Modifier
                .offset(x = 25.dp)
                .padding(top = 14.dp) // Move down
                .wrapContentWidth(Alignment.Start),
            horizontalArrangement = Arrangement.spacedBy(8.5.dp), // 8% of icon size
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Profile Button
            val currentUser by userRepository.currentUser.collectAsState()
            val context = LocalContext.current

            // Get user image URL if available
            val userImageUrl = currentUser?.let { user ->
                user.primaryImageTag?.let { tag ->
                    koinInject<ApiClient>().imageApi.getUserImageUrl(
                        userId = user.id,
                        tag = tag,
                        maxHeight = 100 // Small size for the toolbar
                    )
                }
            }

            // User Profile Button
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(36.dp) // 40dp - 10%
                    .clip(CircleShape)
                    .background(
                        if (isFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = switchUsers,
                    interactionSource = interactionSource,
                    modifier = Modifier.size(40.dp) // 42dp - 5%
                ) {
                    if (userImageUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                AsyncImageView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        Gravity.CENTER
                                    )
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    circleCrop = true
                                    adjustViewBounds = true
                                    setPadding(0, 0, 0, 0)
                                    load(url = userImageUrl)
                                }
                            },
                            modifier = Modifier.size(29.dp) // 31dp - 5%
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_user),
                            contentDescription = stringResource(R.string.lbl_switch_user),
                            modifier = Modifier.size(19.dp), // 21dp - 5%
                            tint = Color.White
                        )
                    }
                }
            }


            // Search Button
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .let { modifier ->
                        if (isSearchFocused) {
                            modifier
                                .width(100.dp)
                                .height(25.dp)
                                .clip(RoundedCornerShape(20.dp))
                        } else {
                            modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        }
                    }
                    .background(
                        if (isSearchFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .let { modifier ->
                            if (isSearchFocused) {
                                modifier
                                    .width(100.dp)
                                    .padding(horizontal = 12.dp)
                            } else {
                                modifier
                                    .size(36.dp)
                            }
                        }
                        .clickable(
                            onClick = openSearch,
                            interactionSource = searchInteractionSource,
                            indication = null
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.lbl_search),
                        tint = if (isSearchFocused) Color.Black else Color.White,
                        modifier = Modifier
                            .let { modifier ->
                                if (isSearchFocused) {
                                    modifier.size(16.dp)
                                } else {
                                    modifier.size(21.dp)
                                }
                            }
                    )


                    if (isSearchFocused) {
                        Text(
                            text = "Search",
                            color = if (isSearchFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 4.dp)
                        )
                    }
                }
            }


            // Library Button
            val libraryInteractionSource = remember { MutableInteractionSource() }
            val isLibraryFocused by libraryInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .let { modifier ->
                        if (isLibraryFocused) {
                            modifier
                                .width(100.dp)
                                .height(25.dp)
                                .clip(RoundedCornerShape(12.5.dp))
                        } else {
                            modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        }
                    }
                    .background(
                        if (isLibraryFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .let { modifier ->
                            if (isLibraryFocused) {
                                modifier
                                    .width(100.dp)
                                    .padding(horizontal = 12.dp)
                            } else {
                                modifier
                                    .size(36.dp)
                            }
                        }
                        .clickable(
                            onClick = openLibrary,
                            interactionSource = libraryInteractionSource,
                            indication = null
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_loop),
                        contentDescription = stringResource(R.string.lbl_library),
                        tint = if (isLibraryFocused) Color.Black else Color.White,
                        modifier = Modifier
                            .let { modifier ->
                                if (isLibraryFocused) {
                                    modifier.size(16.dp)
                                } else {
                                    modifier.size(21.dp)
                                }
                            }
                    )


                    if (isLibraryFocused) {
                        Text(
                            text = "Home",
                            color = if (isLibraryFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 4.dp)
                        )
                    }
                }
            }


            // Live TV Button - Only show if enabled in preferences
            if (showLiveTvButton) {
                val liveTvInteractionSource = remember { MutableInteractionSource() }
                val isLiveTvFocused by liveTvInteractionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .let { modifier ->
                            if (isLiveTvFocused) {
                                modifier
                                    .width(100.dp)
                                    .height(25.dp)
                                    .clip(RoundedCornerShape(12.5.dp))
                            } else {
                                modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            }
                        }
                        .background(
                            if (isLiveTvFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .let { modifier ->
                                if (isLiveTvFocused) {
                                    modifier
                                        .width(100.dp)
                                        .padding(horizontal = 12.dp)
                                } else {
                                    modifier
                                        .size(34.dp)
                                }
                            }
                            .clickable(
                                onClick = openLiveTv,
                                interactionSource = liveTvInteractionSource,
                                indication = null
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_livetv),
                            contentDescription = stringResource(R.string.lbl_live_tv),
                            tint = if (isLiveTvFocused) Color.Black else Color.White,
                            modifier = Modifier
                                .let { modifier ->
                                    if (isLiveTvFocused) {
                                        modifier.size(16.dp)
                                    } else {
                                        modifier.size(22.dp)
                                    }
                                }
                        )

                        // Show text when focused
                        if (isLiveTvFocused) {
                            Text(
                                text = "Live",
                                color = if (isLiveTvFocused) Color.Black else Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // Random Movie Button - Only show if enabled in preferences
            if (showMasksButton) {
                val masksInteractionSource = remember { MutableInteractionSource() }
                val isMasksFocused by masksInteractionSource.collectIsFocusedAsState()
                val context = LocalContext.current
                val api = koinInject<ApiClient>()
                val userViewsRepository = koinInject<UserViewsRepository>()
                val coroutineScope = rememberCoroutineScope()

                fun showError(message: String) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }

                fun getRandomMovie() {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Get all user views
                            val views = userViewsRepository.views.first()

                            // Find the movies library
                            val moviesLibrary = views.find {
                                it.collectionType == CollectionType.MOVIES ||
                                it.name?.equals("Movies", ignoreCase = true) == true
                            }

                            if (moviesLibrary == null) {
                                showError("No Movies library found")
                                return@launch
                            }

                            // Get a random movie from the library
                            val result = api.itemsApi.getItems(
                                parentId = moviesLibrary.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = 1
                            )

                            // The API returns a BaseItemDtoQueryResult which has an items property
                            val movie = result.content.items?.firstOrNull()
                            if (movie != null) {
                                withContext(Dispatchers.Main) {
                                    openRandomMovie(movie)
                                }
                            } else {
                                showError("No movies found in the library")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error getting random movie")
                            showError("Error: ${e.message ?: "Unknown error"}")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .let { modifier ->
                            if (isMasksFocused) {
                                modifier
                                    .width(100.dp)
                                    .height(25.dp)
                                    .clip(RoundedCornerShape(12.5.dp))
                            } else {
                                modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            }
                        }
                        .background(
                            if (isMasksFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .let { modifier ->
                                if (isMasksFocused) {
                                    modifier
                                        .width(100.dp)
                                        .padding(horizontal = 12.dp)
                                } else {
                                    modifier
                                        .size(36.dp)
                                }
                            }
                            .clickable(
                                onClick = { getRandomMovie() },
                                interactionSource = masksInteractionSource,
                                indication = null
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_masks),
                            contentDescription = stringResource(R.string.show_random_button_summary),
                            tint = if (isMasksFocused) Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .let { modifier ->
                                    if (isMasksFocused) {
                                        modifier.size(16.dp)
                                    } else {
                                        modifier.size(21.dp)
                                    }
                                }
                        )

                        // Show text when focused
                        if (isMasksFocused) {
                            Text(
                                text = "Random",
                                color = if (isMasksFocused) Color.Black else Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // Settings Button
            val settingsInteractionSource = remember { MutableInteractionSource() }
            val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .let { modifier ->
                        if (isSettingsFocused) {
                            modifier
                                .width(100.dp)
                                .height(25.dp)
                                .clip(RoundedCornerShape(12.5.dp))
                        } else {
                            modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        }
                    }
                    .background(
                        if (isSettingsFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .let { modifier ->
                            if (isSettingsFocused) {
                                modifier
                                    .width(100.dp)
                                    .padding(horizontal = 12.dp)
                            } else {
                                modifier
                                    .size(36.dp)
                            }
                        }
                        .clickable(
                            onClick = openSettings,
                            interactionSource = settingsInteractionSource,
                            indication = null
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.lbl_settings),
                        tint = if (isSettingsFocused) Color.Black else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .let { modifier ->
                                if (isSettingsFocused) {
                                    modifier.size(16.dp)
                                } else {
                                    modifier.size(24.dp)
                                }
                            }
                    )


                    if (isSettingsFocused) {
                        Text(
                            text = "Settings",
                            color = if (isSettingsFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 4.dp)
                        )
                    }
                }
            }

            // Favorites Button
            val favoritesInteractionSource = remember { MutableInteractionSource() }
            val isFavoritesFocused by favoritesInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .let { modifier ->
                        if (isFavoritesFocused) {
                            modifier
                                .width(110.dp)
                                .height(25.dp)
                                .clip(RoundedCornerShape(12.5.dp))
                        } else {
                            modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        }
                    }
                    .background(
                        if (isFavoritesFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .let { modifier ->
                            if (isFavoritesFocused) {
                                modifier
                                    .width(114.dp)
                                    .padding(horizontal = 13.dp)
                            } else {
                                modifier
                                    .size(36.dp)
                            }
                        }
                        .clickable(
                            onClick = onFavoritesClick,
                            interactionSource = favoritesInteractionSource,
                            indication = null
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = stringResource(R.string.lbl_favorites),
                        tint = if (isFavoritesFocused) Color.Red else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .let { modifier ->
                                if (isFavoritesFocused) {
                                    modifier.size(16.dp)
                                } else {
                                    modifier.size(24.dp)
                                }
                            }
                    )

                    if (isFavoritesFocused) {
                        Text(
                            text = "Favorites",
                            color = if (isFavoritesFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
