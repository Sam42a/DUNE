package org.jellyfin.androidtv.ui.playback.nextup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NEXTUP_TIMER_DISABLED
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ProgressButton
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun NextUpScreen(
	itemId: UUID,
) {
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val viewModel = koinViewModel<NextUpViewModel>()

	val state by viewModel.state.collectAsState()

	LaunchedEffect(itemId) {
		viewModel.setItemId(itemId)
	}

	val item by viewModel.item.collectAsState()
	if (item == null) return

	LaunchedEffect(item?.baseItem) {
	}

	LaunchedEffect(state) {
		when (state) {
			// Open next item
			NextUpState.PLAY_NEXT -> navigationRepository.navigate(Destinations.videoPlayer(0), true)
			// Close activity
			NextUpState.CLOSE -> navigationRepository.goBack()
			// Unknown state
			else -> Unit
		}
	}
	val focusRequester = remember { FocusRequester() }

	Box {
		AppBackground()

		item?.logo?.let { logo ->
			AsyncImage(
				modifier = Modifier
					.align(Alignment.CenterEnd)
					.overscan()
					.height(80.dp),
				url = logo.getUrl(api),
				blurHash = logo.blurHash,
				aspectRatio = logo.aspectRatio ?: 1f,
			)
		}

		NextUpOverlay(
			modifier = Modifier
				.align(Alignment.Center)
				.focusRequester(focusRequester),
			item = requireNotNull(item),
			onConfirm = { viewModel.playNext() },
			onCancel = { viewModel.close() },
		)
	}

	LaunchedEffect(focusRequester) {
		focusRequester.requestFocus()
	}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NextUpOverlay(
	modifier: Modifier = Modifier,
	item: NextUpItemData,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) = ProvideTextStyle(JellyfinTheme.typography.default.copy(color = Color.White)) {

	val api: ApiClient = koinInject()
	val userPreferences: UserPreferences = koinInject()
	val confirmTimer = remember { Animatable(0f) }

	LaunchedEffect(item) {
		val durationMillis = userPreferences[UserPreferences.nextUpTimeout]
		if (durationMillis == NEXTUP_TIMER_DISABLED) {
			confirmTimer.snapTo(0f)
		} else {
			confirmTimer.animateTo(
				targetValue = 1f,
				animationSpec = tween(
					durationMillis = durationMillis,
					easing = LinearEasing
				)
			)
			onConfirm()
		}
	}

	val focusRequester = remember { FocusRequester() }

	Box(
		modifier = modifier
			.fillMaxWidth()
			.overscan()
	) {

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(280.dp)
				.align(Alignment.Center)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color.Transparent,
							Color.Black.copy(alpha = 0.5f)
						)
					)
				)
		)

		Row(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(horizontal = 48.dp, vertical = 32.dp)
				.focusRestorer(focusRequester),
			horizontalArrangement = Arrangement.spacedBy(32.dp),
			verticalAlignment = Alignment.CenterVertically
		) {

			item.thumbnail?.let { thumbnail ->
				AsyncImage(
					modifier = Modifier
						.height(180.dp)
						.aspectRatio(thumbnail.aspectRatio ?: 0.67f)
						.clip(JellyfinTheme.shapes.small),
					url = thumbnail.getUrl(api),
					blurHash = thumbnail.blurHash,
					aspectRatio = thumbnail.aspectRatio ?: 0.67f,
				)
			}

			Column(
				modifier = Modifier.weight(1f)
			) {

				Text(
					text = stringResource(R.string.lbl_next_up),
					fontSize = 42.sp,
				)

				Spacer(Modifier.height(6.dp))

				Text(
					text = item.title,
					fontSize = 20.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)

				Spacer(Modifier.height(24.dp))

				Row(
					modifier = Modifier
						.focusGroup()
						.focusRestorer(focusRequester),
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {

					Button(onClick = onCancel) {
						Text(stringResource(R.string.btn_cancel))
					}

					val coroutineScope = rememberCoroutineScope()

					ProgressButton(
						progress = confirmTimer.value,
						onClick = onConfirm,
						modifier = Modifier
							.focusRequester(focusRequester)
							.onFocusChanged {
								if (!it.isFocused) {
									coroutineScope.launch {
										confirmTimer.snapTo(0f)
									}
								}
							},
					) {
						Text(
							stringResource(R.string.watch_now),
							fontSize = 16.sp
						)
					}
				}
			}
		}
	}
}

class NextUpFragment : Fragment() {
	companion object {
		const val ARGUMENT_ITEM_ID = "item_id"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val id = remember(arguments) {
			arguments?.getString(ARGUMENT_ITEM_ID)?.toUUIDOrNull()
		}
		if (id != null) {
			NextUpScreen(itemId = id)
		}
	}
}
