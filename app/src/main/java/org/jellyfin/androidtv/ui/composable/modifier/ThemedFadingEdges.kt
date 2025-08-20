package org.jellyfin.androidtv.ui.composable.modifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import androidx.core.content.res.getColorOrThrow

/**
 * Creates a modifier that fades the edges of the content using the provided color.
 * @param start The size of the start fade in dp
 * @param top The size of the top fade in dp
 * @param end The size of the end fade in dp
 * @param bottom The size of the bottom fade in dp
 * @param color The color to use for the fade effect
 */
fun Modifier.themedFadingEdges(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
    color: Color
): Modifier = then(
    Modifier
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()

            // Start edge
            if (start.value > 0) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to color,
                        endX = start.toPx(),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }

            // Top edge
            if (top.value > 0) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to color,
                        endY = top.toPx(),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }

            // End edge
            if (end.value > 0) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to color,
                        1f to Color.Transparent,
                        startX = size.width - end.toPx(),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }

            // Bottom edge
            if (bottom.value > 0) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to color,
                        1f to Color.Transparent,
                        startY = size.height - bottom.toPx(),
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
)

/**
 * Creates a modifier that fades all edges of the content using the provided color.
 * @param all The size of all fades in dp
 * @param color The color to use for the fade effect
 */
fun Modifier.themedFadingEdges(
    all: Dp,
    color: Color
): Modifier = themedFadingEdges(
    start = all,
    top = all,
    end = all,
    bottom = all,
    color = color
)

/**
 * Creates a modifier that fades the edges of the content using the provided color.
 * @param vertical The size of the vertical fades in dp
 * @param horizontal The size of the horizontal fades in dp
 * @param color The color to use for the fade effect
 */
fun Modifier.themedFadingEdges(
    vertical: Dp = 0.dp,
    horizontal: Dp = 0.dp,
    color: Color
): Modifier = themedFadingEdges(
    start = horizontal,
    top = vertical,
    end = horizontal,
    bottom = vertical,
    color = color
)

/**
 * Gets the backdrop fading color from the current theme
 */
@Composable
fun getBackdropFadingColor(): Color {
    val context = LocalContext.current
    val typedArray = remember {
        context.theme.obtainStyledAttributes(intArrayOf(R.attr.backdrop_fading_color))
    }
    
    // Store the color in a remembered state to avoid recreating it
    val color = remember(typedArray) {
        try {
            Color(typedArray.getColor(0, 0xFF000000.toInt()))
        } finally {
            // Don't recycle here as it's being remembered
        }
    }
    
    // Use DisposableEffect to properly clean up the TypedArray
    DisposableEffect(Unit) {
        onDispose {
            typedArray.recycle()
        }
    }
    
    return color
}
