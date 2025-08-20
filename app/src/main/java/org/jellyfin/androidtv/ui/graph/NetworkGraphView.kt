package org.jellyfin.androidtv.ui.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class NetworkGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val maxDataPoints = 60 // Store 60 data points (30 seconds at 500ms intervals)
    private val downloadSpeedData = FloatArray(maxDataPoints)
    private val uploadSpeedData = FloatArray(maxDataPoints)
    private var currentIndex = 0
    private var dataCount = 0
    private var maxYValue = 1000f // Start with 1Mbps as minimum scale
    private val dataLock = Object() // Object to synchronize data access
    private val gridLines = 4 // Number of horizontal grid lines
    private val leftPadding = 80f // Space for Y-axis labels
    private val rightPadding = 20f
    private val topPadding = 10f
    private val bottomPadding = 60f // Space for X-axis and legend
    
    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    private val gridPaint = Paint().apply {
        color = Color.argb(50, 255, 255, 255)
        strokeWidth = 1f
    }
    
    private var currentDownload = 0f
    private var currentUpload = 0f
    
    fun addData(downloadSpeed: Float, uploadSpeed: Float) {
        try {
            // Store current values for the indicators
            currentDownload = downloadSpeed.coerceAtLeast(0f)
            currentUpload = uploadSpeed.coerceAtLeast(0f)
            
            // Store data points for the graph
            synchronized(dataLock) {
                // Store the data
                downloadSpeedData[currentIndex] = currentDownload
                uploadSpeedData[currentIndex] = currentUpload
                
                // Calculate new max Y value (with some headroom)
                val currentMax = maxOf(currentDownload, currentUpload) * 1.2f
                maxYValue = maxOf(maxYValue, currentMax, 1000f) // Minimum scale of 1Mbps
                
                // Move to next index (circular buffer)
                currentIndex = (currentIndex + 1) % maxDataPoints
                dataCount = minOf(dataCount + 1, maxDataPoints)
                
                // Force immediate redraw
                postInvalidate()
            }
        } catch (e: Exception) {
            // Silently handle any errors
        }
    }
    
    /**
     * Reset the graph data and state
     */
    fun reset() {
        // Clear all data points
        downloadSpeedData.fill(0f)
        uploadSpeedData.fill(0f)
        currentIndex = 0
        dataCount = 0
        currentDownload = 0f
        currentUpload = 0f
        maxYValue = 1000f // Reset to default max
        
        // Force redraw
        post {
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val width = width.toFloat()
            val height = height.toFloat()
            
            // Calculate graph bounds with padding
            val graphLeft = leftPadding
            val graphRight = width - rightPadding
            val graphTop = topPadding
            val graphBottom = height - bottomPadding
            val graphWidth = graphRight - graphLeft
            val graphHeight = graphBottom - graphTop
            
            // Graph drawing code
            
            // Draw background
            paint.color = Color.argb(25, 255, 255, 255)
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                graphLeft,
                graphTop,
                graphRight,
                graphBottom,
                paint
            )
            
            // Draw grid
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(50, 255, 255, 255)
            paint.strokeWidth = 1f
            
            // Draw vertical grid lines (time)
            for (i in 0..5) {
                val x = graphLeft + (i * graphWidth / 5)
                canvas.drawLine(x, graphTop, x, graphBottom, paint)
            }
            
            // Draw horizontal grid lines (speed)
            for (i in 0..5) {
                val y = graphTop + (i * (graphBottom - graphTop) / 5)
                canvas.drawLine(graphLeft, y, graphRight, y, paint)
            }
            
            // Removed Y-axis title to reduce clutter
            
            // Draw axis lines
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            canvas.drawLine(graphLeft, graphBottom, graphRight, graphBottom, paint) // X-axis
            canvas.drawLine(graphLeft, graphTop, graphLeft, graphBottom, paint)     // Y-axis
            
            // Draw Y-axis labels (speed)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 20f
            textPaint.color = Color.WHITE
            
            for (i in 0..5) {
                val value = (maxYValue * (5 - i) / 5).toInt()
                val y = graphTop + (i * (graphBottom - graphTop) / 5)
                val label = when {
                    value >= 1000000 -> "${value / 1000000}G"
                    value >= 1000 -> "${value / 1000}M"
                    else -> "${value}K"
                }
                canvas.drawText(label, graphLeft - 10, y + 7, textPaint)
            }
            
            // Draw X-axis labels (time)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 18f
            textPaint.color = Color.WHITE
            
            for (i in 0..5) {
                val x = graphLeft + (i * graphWidth / 5)
                val timeLabel = "-${(i * 5)}s"
                canvas.drawText(timeLabel, x, graphBottom + 25, textPaint)
            }
            
            if (dataCount < 2) return  // Need at least 2 points to draw a line
            
            // Calculate point width based on visible points
            val visiblePoints = minOf(dataCount, maxDataPoints)
            val pointStep = graphWidth / (visiblePoints - 1)
            
            // Draw download speed graph (blue)
            paint.color = Color.BLUE
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            
            // Draw download speed line
            val downloadPath = android.graphics.Path()
            var firstPoint = true
            
            for (i in 0 until visiblePoints) {
                val dataIndex = (currentIndex - 1 - i + maxDataPoints * 2) % maxDataPoints
                val x = graphRight - (i * pointStep)
                val y = graphBottom - ((downloadSpeedData[dataIndex] / maxYValue) * graphHeight)
                val clampedY = y.coerceIn(graphTop, graphBottom)
                
                if (firstPoint) {
                    downloadPath.moveTo(x, clampedY)
                    firstPoint = false
                } else {
                    downloadPath.lineTo(x, clampedY)
                }
            }
            
            canvas.drawPath(downloadPath, paint)
            
            // Draw upload speed graph (green)
            paint.color = Color.GREEN
            paint.strokeWidth = 3f
            
            // Draw upload speed line
            val uploadPath = android.graphics.Path()
            firstPoint = true
            
            for (i in 0 until visiblePoints) {
                val dataIndex = (currentIndex - 1 - i + maxDataPoints * 2) % maxDataPoints
                val x = graphRight - (i * pointStep)
                val y = graphBottom - ((uploadSpeedData[dataIndex] / maxYValue) * graphHeight)
                val clampedY = y.coerceIn(graphTop, graphBottom)
                
                if (firstPoint) {
                    uploadPath.moveTo(x, clampedY)
                    firstPoint = false
                } else {
                    uploadPath.lineTo(x, clampedY)
                }
            }
            
            canvas.drawPath(uploadPath, paint)
            
            // Draw current speed indicators in top-right corner with background
            val indicatorX = graphRight - 20f
            var indicatorY = graphTop + 40f
            val indicatorRadius = 8f
            val indicatorTextSize = 24f
            val indicatorPadding = 10f
            val cornerRadius = 8f
            
            // Format speed values
            fun formatSpeed(speed: Float): String {
                return when {
                    speed >= 1000 -> String.format("%.1fM", speed / 1000)
                    else -> "${speed.toInt()}K"
                }
            }
            
            val downloadText = "${formatSpeed(currentDownload)} ↓"
            val uploadText = "${formatSpeed(currentUpload)} ↑"
            
            // Measure text widths
            textPaint.textSize = indicatorTextSize
            val downloadWidth = textPaint.measureText(downloadText)
            val uploadWidth = textPaint.measureText(uploadText)
            val maxWidth = maxOf(downloadWidth, uploadWidth)
            
            // Removed background for cleaner look
            val bgLeft = indicatorX - maxWidth - 2 * indicatorPadding - 30
            val bgTop = indicatorY - indicatorTextSize - 15
            val bgBottom = indicatorY + 25
            val bgRight = indicatorX + 5
            
            // Draw download indicator
            paint.color = Color.BLUE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                indicatorX - maxWidth - indicatorPadding - 5,
                indicatorY - 10,
                indicatorRadius,
                paint
            )
            
            // Draw download text
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.WHITE
            textPaint.textSize = indicatorTextSize
            canvas.drawText(downloadText, indicatorX, indicatorY, textPaint)
            
            // Draw upload indicator
            indicatorY += 30
            paint.color = Color.GREEN
            canvas.drawCircle(
                indicatorX - maxWidth - indicatorPadding - 5,
                indicatorY - 10,
                indicatorRadius,
                paint
            )
            
            // Draw upload text
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.WHITE
            canvas.drawText(uploadText, indicatorX, indicatorY, textPaint)
            
            // Draw legend at the bottom with more spacing
            val legendY = graphBottom + 50f  // Increased from 30f to 50f for more space
            val legendX = graphLeft + 10f
            
            // Draw download legend
            paint.color = Color.BLUE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(legendX + 5, legendY - 5, 5f, paint)
            
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = 16f
            canvas.drawText("Download", legendX + 15, legendY, textPaint)
            
            // Draw upload legend
            paint.color = Color.GREEN
            canvas.drawCircle(legendX + 150, legendY - 5, 5f, paint)
            
            textPaint.color = Color.WHITE
            canvas.drawText("Upload", legendX + 160, legendY, textPaint)
            
            // Draw unit with more spacing from the right edge
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.LTGRAY
            canvas.drawText("kbps", graphRight - 10, legendY, textPaint)
            
            // Draw X-axis title
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.LTGRAY
            canvas.drawText("Time (seconds ago)", width / 2, height - 10, textPaint)
        } catch (e: Exception) {
            // Silently handle any drawing errors
        }
    }
}
