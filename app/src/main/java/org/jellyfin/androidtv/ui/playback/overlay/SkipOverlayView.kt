package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun SkipOverlayComposable(
	visible: Boolean,
) {
	Box(
		contentAlignment = Alignment.BottomEnd,
		modifier = Modifier
			.padding(48.dp)
	) {
		AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
			Row(
				modifier = Modifier
					.clip(RoundedCornerShape(7.dp))
					.background(colorResource(R.color.popup_menu_background).copy(alpha = 0.7f))
					.border(
						width = 1.5.dp,
						color = Color.White.copy(alpha = 0.5f),
						shape = RoundedCornerShape(8.dp)
					)
					.padding(horizontal = 14.dp, vertical = 5.dp)
					.defaultMinSize(minWidth = 10.dp), // Wider background
				horizontalArrangement = Arrangement.spacedBy(3.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				org.jellyfin.androidtv.ui.base.Text(
					text = stringResource(R.string.segment_action_skip).uppercase(),
					color = colorResource(R.color.button_default_normal_text),
					fontSize = 20.sp,
					modifier = Modifier.padding(start = 4.dp, end = 0.dp)
				)

				Image(
					painter = painterResource(R.drawable.ic_skip_next),
					contentDescription =null,
					modifier = Modifier
						.size(30.dp) // Increased icon size from 36.dp to 48.dp
						.padding(end = 1.dp), // Added from your snippet		)
				)
			}
		}
	}
}

class SkipOverlayView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {
	private var mediaSegmentRepository: MediaSegmentRepository? = null
	private val _currentPosition = MutableStateFlow(Duration.ZERO)
	private val _targetPosition = MutableStateFlow<Duration?>(null)
	private val _skipUiEnabled = MutableStateFlow(true)

	var currentPosition: Duration
		get() = _currentPosition.value
		set(value) {
			_currentPosition.value = value
		}

	var currentPositionMs: Long
		get() = _currentPosition.value.inWholeMilliseconds
		set(value) {
			_currentPosition.value = value.milliseconds
		}

	var targetPosition: Duration?
		get() = _targetPosition.value
		set(value) {
			_targetPosition.value = value
		}

	var targetPositionMs: Long?
		get() = _targetPosition.value?.inWholeMilliseconds
		set(value) {
			_targetPosition.value = value?.milliseconds
		}

	var skipUiEnabled: Boolean
		get() = _skipUiEnabled.value
		set(value) {
			_skipUiEnabled.value = value
		}

	fun setMediaSegmentRepository(repository: MediaSegmentRepository) {
		this.mediaSegmentRepository = repository
	}

	val visible: Boolean
		get() {
			val enabled = _skipUiEnabled.value
			val targetPosition = _targetPosition.value
			val currentPosition = _currentPosition.value

			return enabled && targetPosition != null && currentPosition <= (targetPosition - MediaSegmentRepository.SkipMinDuration)
		}

	@Composable
	override fun Content() {
		val skipUiEnabled by _skipUiEnabled.collectAsState()
		val currentPosition by _currentPosition.collectAsState()
		val targetPosition by _targetPosition.collectAsState()

		val visible by remember(skipUiEnabled, currentPosition, targetPosition) {
			derivedStateOf { visible }
		}

		// Auto hide
		LaunchedEffect(skipUiEnabled, targetPosition) {
			val duration = mediaSegmentRepository?.askToSkipAutoHideDuration ?: 8.seconds
			delay(duration)
			_targetPosition.value = null
		}

		SkipOverlayComposable(visible)
	}
}
