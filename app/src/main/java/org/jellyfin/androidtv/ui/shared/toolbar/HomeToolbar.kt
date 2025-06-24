package org.jellyfin.androidtv.ui.shared.toolbar

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.koin.compose.koinInject

@Composable
fun HomeToolbar(
    openSearch: () -> Unit,
    openLiveTv: () -> Unit,
    openSettings: () -> Unit,
    switchUsers: () -> Unit,
    openLibrary: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    userSettingPreferences: UserSettingPreferences = koinInject(),
    userRepository: UserRepository = koinInject()
) {
    // Get the Live TV button preference
    val showLiveTvButton = userSettingPreferences.get(userSettingPreferences.showLiveTvButton)

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
                        if (isFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
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
                    .size(36.dp) // 40dp - 10%
                    .clip(CircleShape)
                    .background(
                        if (isSearchFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = openSearch,
                    interactionSource = searchInteractionSource,
                    modifier = Modifier.size(40.dp) // 42dp - 5%
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.lbl_search),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp) // 22dp - 5%
                    )
                }
            }


            // Library Button
            val libraryInteractionSource = remember { MutableInteractionSource() }
            val isLibraryFocused by libraryInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(36.dp) // 40dp - 10%
                    .clip(CircleShape)
                    .background(
                        if (isLibraryFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = openLibrary,
                    interactionSource = libraryInteractionSource,
                    modifier = Modifier.size(40.dp) // 42dp - 5%
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_loop),
                        contentDescription = stringResource(R.string.lbl_library),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp) // 22dp - 5%
                    )
                }
            }


            // Live TV Button - Only show if enabled in preferences
            if (showLiveTvButton) {
                val liveTvInteractionSource = remember { MutableInteractionSource() }
                val isLiveTvFocused by liveTvInteractionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(34.dp) // 38dp - 10%
                        .clip(CircleShape)
                        .background(
                            if (isLiveTvFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = openLiveTv,
                        interactionSource = liveTvInteractionSource,
                        modifier = Modifier.size(40.dp) // 42dp - 5%
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tv),
                            contentDescription = stringResource(R.string.lbl_live_tv),
                            tint = Color.White,
                            modifier = Modifier.size(21.dp) // 22dp - 5%
                        )
                    }
                }
            }


            // Settings Button
            val settingsInteractionSource = remember { MutableInteractionSource() }
            val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(36.dp) // 40dp - 10%
                    .clip(CircleShape)
                    .background(
                        if (isSettingsFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = openSettings,
                    interactionSource = settingsInteractionSource,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.lbl_settings),
                        tint = if (isSettingsFocused) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Favorites Button
            val favoritesInteractionSource = remember { MutableInteractionSource() }
            val isFavoritesFocused by favoritesInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFavoritesFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onFavoritesClick,
                    interactionSource = favoritesInteractionSource,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = stringResource(R.string.lbl_favorites),
                        tint = if (isFavoritesFocused) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
