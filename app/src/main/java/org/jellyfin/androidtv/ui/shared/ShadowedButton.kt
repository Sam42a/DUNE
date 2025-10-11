import android.graphics.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.ImageViewCompat
import kotlin.math.max
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

fun ImageButton.setButtonShadow(
	drawableId: Int,
	strokeFraction: Float = 0.09f,
	blur: Boolean = true
) {
	post {
		val width = max(width, 1)
		val height = max(height, 1)
		val size = max(width, height)

		val strokePx = (size * strokeFraction).toInt().coerceAtLeast(1)

		val context = context
		val drawable = AppCompatResources.getDrawable(context, drawableId)?.mutate() ?: return@post

		val bitmap = createBitmap(size, size)
		val canvas = Canvas(bitmap)
		drawable.setBounds(0, 0, size, size)
		drawable.draw(canvas)

		val alpha = bitmap.extractAlpha()

		val outBitmap = createBitmap(size, size)
		val outCanvas = Canvas(outBitmap)

		val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.BLACK
			style = Paint.Style.FILL
			if (blur) maskFilter = BlurMaskFilter(strokePx.toFloat(), BlurMaskFilter.Blur.NORMAL)
		}

		outCanvas.drawBitmap(alpha, 0f, 0f, paint)

		outCanvas.drawBitmap(bitmap, 0f, 0f, null)

		ImageViewCompat.setImageTintList(this, null)
		scaleType = ImageView.ScaleType.CENTER_INSIDE
		setImageDrawable(outBitmap.toDrawable(context.resources))

		alpha.recycle()
		bitmap.recycle()
	}
}
