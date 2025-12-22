package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.integration.dream.model.DreamContent

@Composable
fun DreamView(
	content: DreamContent,
	showClock: Boolean,
) = Box(
	modifier = Modifier.fillMaxSize()
) {
	AnimatedContent(
		targetState = content,
		transitionSpec = {
			fadeIn(
				animationSpec = tween(durationMillis = 1000)
			) togetherWith fadeOut(
				animationSpec = tween(durationMillis = 1000)
			)
		},
		label = "DreamContentTransition"
	) { content ->
		when (content) {
			DreamContent.Logo -> DreamContentLogo()
			is DreamContent.LibraryShowcase -> DreamContentLibraryShowcase(content)
			is DreamContent.NowPlaying -> DreamContentNowPlaying(content)
		}
	}

	// Header overlay
	DreamHeader(
		showClock = showClock,
	)
}
