package org.jellyfin.androidtv.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.composable.modifier.getBackdropFadingColor
import org.jellyfin.androidtv.ui.composable.modifier.themedFadingEdges
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
private fun AppThemeBackground() {
	val context = LocalContext.current

	val themeBackground = remember(context.theme) {
		try {
			val attributes = context.theme.obtainStyledAttributes(intArrayOf(R.attr.defaultBackground))
			val drawable = attributes.getDrawable(0)
			attributes.recycle()

			when {
				drawable is ColorDrawable -> {
					drawable.toBitmap(1, 1, Bitmap.Config.ARGB_4444).asImageBitmap()
				}
				drawable != null -> {
					val bitmap = createBitmap(480, 270, Bitmap.Config.RGB_565)
					val canvas = Canvas(bitmap)
					drawable.setBounds(0, 0, canvas.width, canvas.height)
					drawable.draw(canvas)
					bitmap.asImageBitmap()
				}
				else -> null
			}
		} catch (e: OutOfMemoryError) {
			Timber.e(e, "OOM loading theme background")
			null
		} catch (e: Exception) {
			Timber.e(e, "Error loading theme background")
			null
		}
	}

	if (themeBackground != null) {
		Image(
			bitmap = themeBackground,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize()
		)
	} else {
		Box(modifier = Modifier.fillMaxSize().background(Color.Black))
	}
}

@Composable
fun AppBackground() {
	val backgroundService: BackgroundService = koinInject()
	val currentBackground by backgroundService.currentBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()
	val dimmingIntensity by backgroundService.backdropDimmingIntensity.collectAsState()
	val backdropFadingIntensity by backgroundService.backdropFadingIntensity.collectAsState()

	if (!enabled) {
		AppThemeBackground()
		return
	}

	val context = LocalContext.current
	val fadingColor = getBackdropFadingColor()
	val backgroundColor = remember(dimmingIntensity) {
		val typedArray = context.theme.obtainStyledAttributes(intArrayOf(R.attr.background_filter))
		val color = Color(typedArray.getColor(0, 0x000000)).copy(alpha = dimmingIntensity)
		typedArray.recycle()
		color
	}

	AnimatedContent(
		targetState = currentBackground,
		transitionSpec = {
			fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
		}

	) { background ->
		if (background != null) {
			Box(modifier = Modifier.fillMaxSize()) {
				Box(
					modifier = Modifier
						.width(600.dp)
						.aspectRatio(16f / 9f)
						.align(Alignment.TopEnd)
				) {
					Image(
						bitmap = background,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						alignment = Alignment.TopEnd,
						colorFilter = ColorFilter.tint(
							color = backgroundColor,
							blendMode = BlendMode.SrcAtop
						),
						modifier = Modifier
							.fillMaxSize()
							.themedFadingEdges(
								start = (backdropFadingIntensity * 250).toInt().dp,
								bottom = (backdropFadingIntensity * 300).toInt().dp,
								color = fadingColor
							)
					)
				}
			}
		} else {
			AppThemeBackground()
		}
	}
}
