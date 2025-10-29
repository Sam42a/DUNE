package org.jellyfin.androidtv.ui.background

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.composable.modifier.getBackdropFadingColor
import org.jellyfin.androidtv.ui.composable.modifier.themedFadingEdges
import org.koin.compose.koinInject
import timber.log.Timber
import androidx.core.graphics.createBitmap

@Composable
private fun AppThemeBackground() {
    val context = LocalContext.current

    // Use a small bitmap size (1x1) for solid color backgrounds to save memory
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
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = 4 // Downsample by 4x
                    }

                    // Convert drawable to bitmap with reduced size
                    val bitmap = createBitmap(480, 270, Bitmap.Config.RGB_565)

                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)

                    bitmap?.asImageBitmap()
                }
                else -> null
            }
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Failed to load theme background due to OOM")
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
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Fallback to solid black background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}

@Composable
fun AppBackground() {
	val backgroundService: BackgroundService = koinInject()
	val currentBackground by backgroundService.currentBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()
	val dimmingIntensity by backgroundService.backdropDimmingIntensity.collectAsState()
	val backdropFadingIntensity by backgroundService.backdropFadingIntensity.collectAsState()

	// More detailed logging
	Timber.e("AppBackground - Enabled: $enabled")
	Timber.e("AppBackground - Current Background: $currentBackground")
	Timber.e("AppBackground - Dimming Intensity (raw): $dimmingIntensity")
	Timber.e("AppBackground - Dimming Intensity (applied): $dimmingIntensity")

	// Add a fallback for when background is not enabled
	if (!enabled) {
		Timber.e("AppBackground - Background is NOT enabled!")
		AppThemeBackground()
		return
	}

	var isImageReady by remember { mutableStateOf(false) }

	if (currentBackground != null) {
		isImageReady = true
	}

	val localContext = LocalContext.current

	AnimatedContent(
		targetState = currentBackground,
		transitionSpec = {
			fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
		},
		label = "BackgroundTransition",
	) { background ->
		if (background != null) {
			// Get the background filter color from the theme
			val typedArray = localContext.theme.obtainStyledAttributes(
				intArrayOf(R.attr.background_filter)
			)
			val backgroundColor = Color(typedArray.getColor(0, 0x000000)).copy(alpha = dimmingIntensity)
			typedArray.recycle()

			val fadingColor = getBackdropFadingColor()
			Box(Modifier.fillMaxSize()) {
				if (isImageReady) {
					// Container for top-right positioned backdrop
					Box(
						modifier = Modifier
							.width(600.dp)  // 20% larger (500 * 1.2 = 600)
							.aspectRatio(16f / 9f)  // Maintain 16:9 aspect ratio
							.align(Alignment.TopEnd)  // Position in top-right corner
					) {
						Image(
							bitmap = background,
							contentDescription = null,
							modifier = Modifier
								.fillMaxSize()
								.themedFadingEdges(
									start = (backdropFadingIntensity * 250).toInt().dp,  // Fade from left
									bottom = (backdropFadingIntensity * 300).toInt().dp,  // Fade from bottom
									color = fadingColor
								),
							contentScale = ContentScale.Crop,
							alignment = Alignment.TopEnd,  // Align content to top-right
							colorFilter = ColorFilter.tint(
								color = backgroundColor,
								blendMode = BlendMode.SrcAtop
							)
						)
					}
				}
			}
		} else {
			Timber.e("AppBackground - Background is NULL, using AppThemeBackground")
			AppThemeBackground()
		}
	}
}
