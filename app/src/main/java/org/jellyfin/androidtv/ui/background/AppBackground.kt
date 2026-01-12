package org.jellyfin.androidtv.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.composable.modifier.getBackdropFadingColor
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
private fun AppThemeBackground() {
	val context = LocalContext.current
	val themeBackground by remember { derivedStateOf { loadThemeBackground(context) } }

	when (val bg = themeBackground) {
		is BackgroundResult.Bitmap -> {
			Image(
				bitmap = bg.imageBitmap,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize()
			)
		}
		is BackgroundResult.Color -> {
			Box(modifier = Modifier.fillMaxSize().background(bg.color))
		}
		BackgroundResult.Fallback -> {
			Box(modifier = Modifier.fillMaxSize().background(Color.Black))
		}
	}
}

private sealed class BackgroundResult {
	data class Bitmap(val imageBitmap: ImageBitmap) : BackgroundResult()
	data class Color(val color: androidx.compose.ui.graphics.Color) : BackgroundResult()
	object Fallback : BackgroundResult()
}

private fun loadThemeBackground(context: Context): BackgroundResult {
	return try {
		val attributes = context.theme.obtainStyledAttributes(intArrayOf(R.attr.defaultBackground))
		val drawable = attributes.getDrawable(0)
		attributes.recycle()

		when (drawable) {
			is ColorDrawable -> BackgroundResult.Color(androidx.compose.ui.graphics.Color(drawable.color))
			null -> BackgroundResult.Fallback
			else -> {
				val bitmap = createBitmap(240, 135, Bitmap.Config.RGB_565)
				val canvas = Canvas(bitmap)
				drawable.setBounds(0, 0, 240, 135)
				drawable.draw(canvas)
				BackgroundResult.Bitmap(bitmap.asImageBitmap())
			}
		}
	} catch (e: Exception) {
		Timber.e(e, "Error loading theme background")
		BackgroundResult.Fallback
	}
}

private suspend fun extractColorsFromBitmap(imageBitmap: ImageBitmap): ExtractedColors {
	return withContext(Dispatchers.Default) {
		try {
			val androidBitmap = createBitmap(imageBitmap.width, imageBitmap.height)
			val buffer = IntArray(imageBitmap.width * imageBitmap.height)
			imageBitmap.readPixels(buffer)
			androidBitmap.setPixels(buffer, 0, imageBitmap.width, 0, 0, imageBitmap.width, imageBitmap.height)

			val scaledBitmap = if (androidBitmap.width > 200 || androidBitmap.height > 200)
				androidBitmap.scale(200, 200, false).also { androidBitmap.recycle() }
			else androidBitmap

			val palette = Palette.from(scaledBitmap).generate()
			scaledBitmap.recycle()

			val vibrant = palette.vibrantSwatch
			val darkVibrant = palette.darkVibrantSwatch
			val lightVibrant = palette.lightVibrantSwatch
			val muted = palette.mutedSwatch
			val darkMuted = palette.darkMutedSwatch

			fun Palette.Swatch?.isCoolColor(): Boolean {
				if (this == null) return false
				val r = (rgb shr 16) and 0xFF
				val g = (rgb shr 8) and 0xFF
				val b = rgb and 0xFF
				return b > r && (b + g) > (r * 1.5f)
			}

			fun toColor(swatch: Palette.Swatch?, alpha: Float): Color {
				return swatch?.rgb?.let { Color(it).copy(alpha = alpha) } ?: Color.Transparent
			}

			val primaryColor = toColor(darkVibrant ?: darkMuted, 0.4f)
			val secondaryColor = when {
				vibrant != null && vibrant.isCoolColor() -> toColor(vibrant, 0.4f)
				muted != null && muted.isCoolColor() -> toColor(muted, 0.4f)
				vibrant != null -> toColor(vibrant, 0.4f)
				muted != null -> toColor(muted, 0.4f)
				else -> Color.Transparent
			}
			val tertiaryColor = toColor(vibrant ?: lightVibrant, 0.35f)

			ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
		} catch (e: Exception) {
			Timber.e(e, "Error extracting palette colors")
			ExtractedColors(Color.Transparent, Color.Transparent, Color.Transparent)
		}
	}
}

data class ExtractedColors(
	val primary: Color,
	val secondary: Color,
	val tertiary: Color
)

@Composable
fun AppBackground() {
	val backgroundService: BackgroundService = koinInject()
	val blockAllBackgrounds by backgroundService.blockAllBackgrounds.collectAsState()

	if (blockAllBackgrounds) {
		AppThemeBackground()
		return
	}

	val currentBackground by backgroundService.currentBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()
	val backdropFadingIntensity by backgroundService.backdropFadingIntensity.collectAsState()
	val backdropDynamicColors by backgroundService.backdropDynamicColors.collectAsState()

	var primaryColor by remember { mutableStateOf<Color?>(null) }
	var secondaryColor by remember { mutableStateOf<Color?>(null) }
	var tertiaryColor by remember { mutableStateOf<Color?>(null) }
	var showImage by remember { mutableStateOf(false) }
	var lastValidBackground by remember { mutableStateOf<Any?>(null) }

	LaunchedEffect(currentBackground) {
		if (currentBackground != null) {
			lastValidBackground = currentBackground
		}
	}

	val displayBackground = currentBackground ?: lastValidBackground

	val animatedPrimaryColor by animateColorAsState(
		targetValue = primaryColor ?: Color.Transparent,
		animationSpec = tween(durationMillis = 700, easing = LinearEasing),
		label = "primary_color"
	)
	val animatedSecondaryColor by animateColorAsState(
		targetValue = secondaryColor ?: Color.Transparent,
		animationSpec = tween(durationMillis = 700, easing = LinearEasing),
		label = "secondary_color"
	)
	val animatedTertiaryColor by animateColorAsState(
		targetValue = tertiaryColor ?: Color.Transparent,
		animationSpec = tween(durationMillis = 700, easing = LinearEasing),
		label = "tertiary_color"
	)

	LaunchedEffect(currentBackground, backdropDynamicColors) {
		if (currentBackground != null && backdropDynamicColors) {
			delay(600)
			val background = currentBackground
			if (background != null) {
				try {
					val extracted = extractColorsFromBitmap(background)
					primaryColor = extracted.primary
					secondaryColor = extracted.secondary
					tertiaryColor = extracted.tertiary
					showImage = true
				} catch (e: Exception) {
					Timber.e(e, "Error extracting colors")
					primaryColor = null
					secondaryColor = null
					tertiaryColor = null
					showImage = false
				}
			} else {
				showImage = false
			}
		} else {
			if (!backdropDynamicColors) {
				primaryColor = null
				secondaryColor = null
				tertiaryColor = null
			}
			showImage = !backdropDynamicColors
		}
	}

	if (!enabled) {
		AppThemeBackground()
		return
	}

	val context = LocalContext.current
	val fallbackBackgroundColor = remember {
		val typedArray = context.theme.obtainStyledAttributes(intArrayOf(R.attr.background_filter))
		val color = Color(typedArray.getColor(0, 0x000000))
		typedArray.recycle()
		color
	}

	val solidFadingColor = getBackdropFadingColor()
	val fadingColor = if (backdropDynamicColors) animatedPrimaryColor else solidFadingColor
	val primaryGradientColor = if (backdropDynamicColors) animatedPrimaryColor else Color.Transparent
	val secondaryGradientColor = if (backdropDynamicColors) animatedSecondaryColor else Color.Transparent
	val tertiaryGradientColor = if (backdropDynamicColors) animatedTertiaryColor else Color.Transparent

	Box(modifier = Modifier.fillMaxSize()) {
		if (displayBackground != null) {
			Box(modifier = Modifier.fillMaxSize()) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.drawBehind {
							drawRect(color = fadingColor)

							if (secondaryGradientColor != Color.Transparent && backdropFadingIntensity > 0) {
								drawRect(
									brush = Brush.radialGradient(
										colors = listOf(secondaryGradientColor, Color.Transparent),
										center = androidx.compose.ui.geometry.Offset(0f, 0f),
										radius = size.width * 0.4f,
									)
								)
							}

							if (primaryGradientColor != Color.Transparent && backdropFadingIntensity > 0) {
								drawRect(
									brush = Brush.radialGradient(
										colors = listOf(primaryGradientColor, Color.Transparent),
										center = androidx.compose.ui.geometry.Offset(size.width, size.height),
										radius = size.width * 0.4f,
									)
								)
							}

							if (tertiaryGradientColor != Color.Transparent && backdropFadingIntensity > 0) {
								drawRect(
									brush = Brush.radialGradient(
										colors = listOf(tertiaryGradientColor, Color.Transparent),
										center = androidx.compose.ui.geometry.Offset(size.width, 0f),
										radius = size.width * 0.4f,
									)
								)
							}
						}
				)

				Box(modifier = Modifier.fillMaxSize()) {
					if (showImage && displayBackground != null) {
						Box(
							modifier = Modifier
								.width(650.dp)
								.aspectRatio(16f / 10f)
								.align(Alignment.TopEnd)
						) {
							AnimatedContent(
								targetState = showImage,
								transitionSpec = {
									fadeIn(
										animationSpec = tween(durationMillis = 600, delayMillis = 100, easing = LinearEasing)
									) togetherWith fadeOut(
										animationSpec = tween(durationMillis = 600, easing = LinearEasing)
									)
								},
								label = "image_transition"
							) { _ ->
								if (displayBackground is ImageBitmap) {
									Image(
										bitmap = displayBackground,
										contentDescription = null,
										contentScale = ContentScale.Crop,
										modifier = Modifier
											.fillMaxSize()
											.alpha(0.95f)
											.drawWithContent {
												drawContent()
												if (backdropFadingIntensity > 0) { // More is less & less is more here
													val horizontalFadeSize = (1.7f - backdropFadingIntensity) * 62f
													val verticalFadeSize = (1.5f - backdropFadingIntensity) * 320f

													drawRect(
														brush = Brush.horizontalGradient(
															colors = listOf(Color.Transparent, Color.Black),
															startX = 0f,
															endX = horizontalFadeSize.dp.toPx(),
														),
														blendMode = BlendMode.DstIn,
													)
													drawRect(
														brush = Brush.verticalGradient(
															colors = listOf(Color.Black, Color.Transparent),
															startY = 0f,
															endY = verticalFadeSize.dp.toPx(),
														),
														blendMode = BlendMode.DstIn,
													)
												}
											}
									)
								}
							}
						}
					}
				}
			}
		} else {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(fadingColor)
					.alpha(0.95f)
			)
		}
	}
}
