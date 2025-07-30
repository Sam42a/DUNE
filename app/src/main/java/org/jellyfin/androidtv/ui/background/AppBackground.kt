package org.jellyfin.androidtv.ui.background

import android.graphics.drawable.ColorDrawable
import timber.log.Timber
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.composable.modifier.getBackdropFadingColor
import org.jellyfin.androidtv.ui.composable.modifier.themedFadingEdges
import org.koin.compose.koinInject

@Composable
private fun AppThemeBackground() {
    val context = LocalContext.current
    val themeBackground = remember(context.theme) {
        val attributes = context.theme.obtainStyledAttributes(intArrayOf(R.attr.defaultBackground))
        val drawable = attributes.getDrawable(0)
        attributes.recycle()

        if (drawable is ColorDrawable) drawable.toBitmap(1, 1).asImageBitmap()
        else drawable?.toBitmap(1920, 720)?.asImageBitmap()
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
    val blurBackground by backgroundService.blurBackground.collectAsState()
    val blurIntensity by backgroundService.blurIntensity.collectAsState()
    val enabled by backgroundService.enabled.collectAsState()
    val dimmingIntensity by backgroundService.backdropDimmingIntensity.collectAsState()
    val backdropFadingIntensity by backgroundService.backdropFadingIntensity.collectAsState()

    // More detailed logging
    Timber.e("AppBackground - Enabled: $enabled")
    Timber.e("AppBackground - Current Background: $currentBackground")
    Timber.e("AppBackground - Blur Background: $blurBackground")
    Timber.e("AppBackground - Blur Intensity: $blurIntensity")
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
            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
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
                                .then(if (blurBackground) Modifier.blur((blurIntensity * 20).dp) else Modifier)
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
