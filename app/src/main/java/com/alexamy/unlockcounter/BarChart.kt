package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Bars left to right in [slots] equal slots; the [highlight] bar uses the accent color, a null value is drawn as a dashed empty slot, unused slots stay blank. */
class BarChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    /** A bar's value, null = no data, and the label spoken before it. */
    data class Bar(val value: Int?, val label: String)

    private var values: List<Int?> = emptyList()
    private var slots = 0

    /** Shows [bars] and describes them to screen readers as "[title]: label: value, …". */
    fun show(title: String, bars: List<Bar>, slots: Int = bars.size) {
        values = bars.map { it.value }
        this.slots = slots
        val noData = context.getString(R.string.no_data)
        contentDescription = "$title: " + bars.joinToString { "${it.label}: ${it.value ?: noData}" }
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

    /** Called with +1 for a swipe to the left (next) and -1 for a swipe to the right (previous). */
    var onSwipe: ((Int) -> Unit)? = null

    private var pressed: Int? = null
    private var tapped: Int? = null
    private var downX = 0f
    private var downY = 0f

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
        val gap = minOf(6 * density, width * 0.3f / slots)
        val w = (width - gap * (slots - 1)) / slots
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
        if (onBarClick == null && onSwipe == null || values.isEmpty()) return false
        val index = (event.x / width * slots).toInt().coerceIn(0, slots - 1).takeIf { it <= values.lastIndex }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = index
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> if (index != pressed) pressed = null
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (abs(dx) > SWIPE * density && abs(dx) > abs(event.y - downY)) {
                    onSwipe?.invoke(if (dx < 0) 1 else -1)
                } else if (index != null && index == pressed && onBarClick != null) {
                    tapped = index
                    performClick()
                }
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

    private companion object {
        const val SWIPE = 48
    }
}
