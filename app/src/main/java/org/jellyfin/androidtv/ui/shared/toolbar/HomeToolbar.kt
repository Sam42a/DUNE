package org.jellyfin.androidtv.ui.shared.toolbar

import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Text
import androidx.compose.animation.core.FastOutSlowInEasing

@Composable
fun AnimatedIconButton(
	modifier: Modifier = Modifier,
	iconRes: Int,
	text: String,
	onClick: () -> Unit,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
	focusedShapeRadius: Dp = 12.5.dp,
	unfocusedSize: Dp = 36.dp,
	focusedHeight: Dp = 25.dp,
	unfocusedIconSize: Dp = 24.dp,
	focusedIconSize: Dp = 16.dp,
	unfocusedAlpha: Float = 0.7f
) {
	val isFocused by interactionSource.collectIsFocusedAsState()

	val horizontalPadding by animateDpAsState(
		targetValue = if (isFocused) 12.dp else 0.dp,
		animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
	)

	val backgroundColor by animateColorAsState(
		targetValue = if (isFocused) Color.White.copy(alpha = 0.65f) else Color.Transparent,
		animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
	)

	Box(
		modifier = modifier
			.height(focusedHeight)
			.clip(RoundedCornerShape(focusedShapeRadius))
			.background(backgroundColor)
			.animateContentSize(
				animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
			),
		contentAlignment = Alignment.Center
	) {
		Row(
			modifier = Modifier
				.clickable(
					onClick = onClick,
					interactionSource = interactionSource,
					indication = null
				)
				.padding(horizontal = horizontalPadding)
				.height(if (isFocused) focusedHeight else unfocusedSize),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				painter = painterResource(iconRes),
				contentDescription = text,
				tint = if (isFocused) Color.Black else Color.White.copy(alpha = unfocusedAlpha),
				modifier = Modifier.size(if (isFocused) focusedIconSize else unfocusedIconSize)
			)

			AnimatedVisibility(
				visible = isFocused,
				enter = fadeIn(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
					expandHorizontally(animationSpec = tween(150, easing = FastOutSlowInEasing)),
				exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
					shrinkHorizontally(animationSpec = tween(150, easing = FastOutSlowInEasing))
			) {
				Text(
					text = text,
					color = Color.Black,
					fontSize = 14.sp,
					modifier = Modifier.padding(start = 4.dp)
				)
			}
		}
	}
}

@RequiresApi(Build.VERSION_CODES.M)
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
			AnimatedIconButton(
				iconRes = R.drawable.ic_search,
				text = stringResource(R.string.lbl_search),
				onClick = openSearch
			)

			// Library Button
			AnimatedIconButton(
				iconRes = R.drawable.ic_loop,
				text = stringResource(R.string.lbl_home),
				onClick = openLibrary
			)

			// Live TV Button - Only show if enabled in preferences
			if (showLiveTvButton) {
				AnimatedIconButton(
					iconRes = R.drawable.ic_livetv,
					text = stringResource(R.string.lbl_live),
					onClick = openLiveTv,
				)
			}

			// Random Movie Button - Only show if enabled in preferences
			if (showMasksButton) {
				val context = LocalContext.current
				val api = koinInject<ApiClient>()
				val userViewsRepository = koinInject<UserViewsRepository>()
				val coroutineScope = rememberCoroutineScope()
				val errorMessage = stringResource(R.string.msg_no_items)

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
								showError(errorMessage)
							}
						} catch (e: Exception) {
							Timber.e(e, "Error getting random movie")
							showError("Error: ${e.message ?: "Unknown error"}")
						}
					}
				}

				AnimatedIconButton(
					iconRes = R.drawable.ic_dice,
					text = stringResource(R.string.random),
					onClick = { getRandomMovie() }
				)
			}

			// Settings Button
			AnimatedIconButton(
				iconRes = R.drawable.ic_settings,
				text = stringResource(R.string.settings_title),
				onClick = openSettings
			)

			// Favorites Button
			AnimatedIconButton(
				iconRes = R.drawable.ic_heart,
				text = stringResource(R.string.lbl_favorites),
				onClick = onFavoritesClick
			)
		}
	}
}
