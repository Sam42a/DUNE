package org.jellyfin.androidtv.ui.shared.buttons

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R

/**
 * Android TV compatible button with reliable DPAD focus visuals.
 */
class DetailButton @JvmOverloads constructor(
	context: Context
) : FrameLayout(context) {

	private var composeView: ComposeView
	private var currentText: String? = null
	private var currentIcon: Int = 0
	private var currentProgress: Float = 0f
	private var clickListener: OnClickListener? = null
	private var _isVisible = true
	private var _isActivated = false

	init {
		isFocusable = true
		isFocusableInTouchMode = true

		composeView = ComposeView(context).apply {
			isFocusable = false
			descendantFocusability = FOCUS_BLOCK_DESCENDANTS
		}

		addView(composeView)

		layoutParams = LayoutParams(
			LayoutParams.WRAP_CONTENT,
			LayoutParams.WRAP_CONTENT
		)

		super.setOnClickListener {
			clickListener?.onClick(it)
		}

		updateComposeContent()
	}

	override fun setOnClickListener(listener: OnClickListener?) {
		clickListener = listener
	}

	fun setLabel(text: String?) {
		currentText = text
		updateComposeContent()
	}

	fun setIcon(icon: Int) {
		currentIcon = icon
		updateComposeContent()
	}

	fun setProgress(progress: Float) {
		currentProgress = progress
		updateComposeContent()
	}

	override fun setActivated(activated: Boolean) {
		super.setActivated(activated)
		_isActivated = activated
		updateComposeContent()
	}

	private fun updateComposeContent() {
		composeView.setContent {
			DetailButtonComposable(
				text = currentText,
				icon = currentIcon,
				progress = currentProgress,
				isActivated = _isActivated,
				onClick = { clickListener?.onClick(this@DetailButton) }
			)
		}
	}

	@Composable
	private fun DetailButtonComposable(
		text: String?,
		icon: Int,
		progress: Float,
		isActivated: Boolean,
		onClick: () -> Unit
	) {
		var isFocused by remember { mutableStateOf(false) }

		DisposableEffect(Unit) {
			val listener = OnFocusChangeListener { _, hasFocus ->
				isFocused = hasFocus
			}
			setOnFocusChangeListener(listener)
			onDispose { setOnFocusChangeListener(null) }
		}

		LaunchedEffect(Unit) {
			while (true) {
				delay(100)
				if (isFocused != isFocused()) {
					isFocused = isFocused()
				}
			}
		}

		val isResumeButton = icon == R.drawable.ic_resume

		val backgroundColor = when {
			isFocused -> Color.White

			isActivated -> when (icon) {
				R.drawable.ic_heart ->
					Color(0xFFFF0000).copy(alpha = 0.4f)

				R.drawable.ic_watch ->
					Color(0xFF00FF00).copy(alpha = 0.2f)

				else ->
					Color.White.copy(alpha = 0.12f)
			}

			else -> Color.White.copy(alpha = 0.12f)
		}

		val foregroundColor = when {
			isFocused -> Color.Black
			isActivated -> Color.White
			else -> Color.White.copy(alpha = 0.85f)
		}

		Box(
			modifier = Modifier
				.animateContentSize()
				.clip(RoundedCornerShape(14.dp))
				.background(backgroundColor)
				.drawBehind {
					if (isResumeButton && progress > 0f) {
						drawRect(
							color = Color.White.copy(alpha = 0.3f),
							size = size.copy(
								width = size.width * progress.coerceIn(0f, 1f)
							)
						)
					}
				}
				.clickable(onClick = onClick)
				.padding(horizontal = 8.dp, vertical = 9.dp)
				.height(16.dp),
			contentAlignment = Alignment.Center
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.Center
			) {
				Icon(
					painter = painterResource(icon),
					contentDescription = text,
					tint = foregroundColor,
					modifier = Modifier.size(14.dp)
				)

				if (text != null) {
					Text(
						text = text,
						fontSize = 11.sp,
						fontWeight = FontWeight.Medium,
						color = foregroundColor,
						modifier = Modifier.padding(start = 4.dp)
					)
				}
			}
		}
	}

	companion object {
		@JvmStatic
		fun create(
			context: Context,
			icon: Int,
			text: String,
			onClick: OnClickListener
		): DetailButton {
			return DetailButton(context).apply {
				setIcon(icon)
				setLabel(text)
				setOnClickListener(onClick)
			}
		}
	}
}
