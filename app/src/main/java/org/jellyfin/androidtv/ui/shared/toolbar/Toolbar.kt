package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.modifier.childFocusRestorer
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.composable.rememberCurrentTime

@Composable
fun Logo(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.size(30.dp)
	) {
		Image(
			painter = painterResource(R.drawable.app_logo),
			contentDescription = stringResource(R.string.app_name),
			modifier = Modifier.fillMaxSize(),
			contentScale = ContentScale.Fit
		)
	}
}

@Composable
fun Toolbar(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(95.dp)
			.background(Color.Transparent)
			.overscan(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier = Modifier
				.weight(1f)
		) {
			content()
		}

		val currentTime by rememberCurrentTime()
		// Clock positioned further to the left
		Text(
			text = currentTime,
			fontSize = 14.sp,
			color = Color.White,
			modifier = Modifier
				.padding(end = 0.dp) // Reduced from 34dp to 24dp (about 30% less than previous)
                .padding(horizontal = 4.dp) // Reduced horizontal padding
                .background(Color.Transparent) // Keep background transparent
		)
	}
}

@Composable
fun BoxScope.ToolbarButtons(
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier = Modifier
			.childFocusRestorer()
			.align(Alignment.CenterEnd)
			.padding(end = 4.dp), // Add some right padding to the entire row
		horizontalArrangement = Arrangement.spacedBy(4.dp), // Reduced from 8.dp to 4.dp
	) {
		JellyfinTheme(
			colorScheme = JellyfinTheme.colorScheme.copy(
				button = Color.Transparent
			)
		) {
			content()
		}
	}
}
