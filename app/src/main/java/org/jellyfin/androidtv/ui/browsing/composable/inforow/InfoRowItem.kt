package org.jellyfin.androidtv.ui.browsing.composable.inforow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.ProvideTextStyle

/**
 * A single item in the [BaseItemInfoRow].
 */
@Composable
fun InfoRowItem(
	// Icon options
	icon: Painter? = null,
	contentDescription: String?,
	// Styling
	colors: Pair<Color, Color> = InfoRowColors.Transparent,
	backgroundCornerRadius: androidx.compose.ui.unit.Dp = 1.dp,
	horizontalPadding: androidx.compose.ui.unit.Dp = 1.dp,
	textSize: androidx.compose.ui.unit.TextUnit = 10.sp,
	// Content
	content: @Composable RowScope.() -> Unit,
) {
	val (backgroundColor, foregroundColor) = colors

	val modifier = when {
		backgroundColor.alpha > 0f -> Modifier
			.background(backgroundColor, RoundedCornerShape(backgroundCornerRadius))
			.padding(horizontal = horizontalPadding)

		else -> Modifier
	}

	ProvideTextStyle(
		value = TextStyle(
			color = foregroundColor,
			fontSize = if (backgroundColor.alpha > 0f) textSize else 14.sp,
			fontWeight = FontWeight.SemiBold,
		)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(3.dp),
			verticalAlignment = Alignment.CenterVertically,
			modifier = modifier.fillMaxHeight(),
		) {
			if (icon != null) {
                Image(
                    painter = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(12.dp),
                )
            }

			content()
		}
	}
}
