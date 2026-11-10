package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Bars left to right; the [highlight] bar uses the accent color, a null value is drawn as a dashed empty slot. */
class BarChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var values: List<Int?> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var highlight: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the index of the tapped bar; null makes the chart ignore touches. A click without a touch, as from TalkBack, picks the highlighted bar. */
    var onBarClick: ((Int) -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }

    private var pressed: Int? = null
    private var tapped: Int? = null

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
        val gap = minOf(6 * density, width * 0.3f / values.size)
        val w = (width - gap * (values.size - 1)) / values.size
        val radius = minOf(4 * density, w / 2)
        val max = values.filterNotNull().maxOrNull()?.coerceAtLeast(1) ?: 1
        values.forEachIndexed { i, value ->
            val left = i * (w + gap)
            if (value == null) {
                canvas.drawRoundRect(left, 0f, left + w, height.toFloat(), radius, radius, empty)
            } else {
                val h = maxOf(2 * density, height * value.toFloat() / max)
                bar.color = context.getColor(if (i == highlight) R.color.accent else R.color.bar_past)
                canvas.drawRoundRect(left, height - h, left + w, height.toFloat(), radius, radius, bar)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onBarClick == null || values.isEmpty()) return false
        val index = (event.x / width * values.size).toInt().coerceIn(0, values.lastIndex)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressed = index
            MotionEvent.ACTION_MOVE -> if (index != pressed) pressed = null
            MotionEvent.ACTION_UP -> if (index == pressed) {
                tapped = index
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> pressed = null
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val index = tapped ?: highlight ?: values.lastIndex
        tapped = null
        onBarClick?.invoke(index)
        return true
    }
}
