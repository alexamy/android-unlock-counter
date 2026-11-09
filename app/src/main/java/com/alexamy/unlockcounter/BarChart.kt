package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Bars oldest to newest; the newest is highlighted, a null value is drawn as a dashed empty slot. */
class BarChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var values: List<Int?> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = context.getColor(R.color.bar_past)
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val gap = 6 * density
        val radius = 4 * density
        val w = (width - gap * (values.size - 1)) / values.size
        val max = values.filterNotNull().maxOrNull()?.coerceAtLeast(1) ?: 1
        values.forEachIndexed { i, value ->
            val left = i * (w + gap)
            if (value == null) {
                canvas.drawRoundRect(left, 0f, left + w, height.toFloat(), radius, radius, empty)
            } else {
                val h = maxOf(2 * density, height * value.toFloat() / max)
                bar.color = context.getColor(if (i == values.lastIndex) R.color.accent else R.color.bar_past)
                canvas.drawRoundRect(left, height - h, left + w, height.toFloat(), radius, radius, bar)
            }
        }
    }
}
