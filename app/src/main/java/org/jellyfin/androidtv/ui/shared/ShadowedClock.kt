package org.jellyfin.androidtv.ui.shared

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.TextClock

class ShadowedClock @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : TextClock(context, attrs, defStyleAttr) {

	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		color = Color.BLACK
	}
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.WHITE
	}
	private var strokeWidthFraction = 0.09f
	private var blur = true

	@SuppressLint("DrawAllocation")
	override fun onDraw(canvas: Canvas) {
		val textStr = text?.toString() ?: return
		if (textStr.isEmpty()) return

		strokePaint.set(paint)
		strokePaint.style = Paint.Style.STROKE
		strokePaint.strokeWidth = paint.textSize * strokeWidthFraction
		strokePaint.color = Color.BLACK
		strokePaint.maskFilter = if (blur) BlurMaskFilter(strokePaint.strokeWidth, BlurMaskFilter.Blur.NORMAL) else null

		fillPaint.set(paint)
		fillPaint.style = Paint.Style.FILL
		fillPaint.color = Color.WHITE
		fillPaint.maskFilter = null

		val x = paddingLeft.toFloat()
		val y = paddingTop.toFloat() - paint.ascent()

		canvas.drawText(textStr, x, y, strokePaint)
		canvas.drawText(textStr, x, y, fillPaint)
	}
}
