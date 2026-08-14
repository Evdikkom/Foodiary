package com.example.foodiary.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.foodiary.R
import kotlin.math.min
import kotlin.math.roundToInt

class CalorieRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringBounds = RectF()
    private val strokeWidth = dp(12f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@CalorieRingView.strokeWidth
        color = 0x55FFFFFF
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@CalorieRingView.strokeWidth
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.white)
        textAlign = Paint.Align.CENTER
        textSize = sp(20f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDDFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }

    private var currentValue = 0f
    private var maxValue = 3500f

    fun setValues(value: Float, max: Float) {
        currentValue = value.coerceAtLeast(0f)
        maxValue = if (max <= 0f) 1f else max
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(170f).roundToInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = strokeWidth / 2f + dp(6f)
        ringBounds.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(ringBounds, -90f, 360f, false, trackPaint)

        val sweep = (currentValue / maxValue).coerceIn(0f, 1f) * 360f
        canvas.drawArc(ringBounds, -90f, sweep, false, progressPaint)

        val centerX = width / 2f
        val centerY = height / 2f

        val valueText = currentValue.roundToInt().toString()
        canvas.drawText(valueText, centerX, centerY - dp(2f), valuePaint)
        canvas.drawText("ккал", centerX, centerY + dp(18f), unitPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
