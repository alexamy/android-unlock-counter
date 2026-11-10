package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import java.time.OffsetDateTime
import kotlin.math.abs

/** 24 hours left to right with a tick per unlock; gaps of an hour or more are shaded and the longest gap is outlined. */
class DayStrip(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var times: List<OffsetDateTime> = emptyList()
        set(value) {
            field = value
            seconds = value.map { it.toLocalTime().toSecondOfDay() }
            invalidate()
        }

    /** Indices of [times] drawn in the accent color. */
    var lit: IntRange = IntRange.EMPTY
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the index of the unlock nearest to a tap. A click without a touch, as from TalkBack, picks the newest one. */
    var onTap: ((Int) -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }

    private var seconds: List<Int> = emptyList()
    private var downX = 0f
    private var downY = 0f
    private var pressed = false
    private var tapped: Int? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_high) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = context.getColor(R.color.accent)
    }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bar_past) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_muted)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }

    private fun x(second: Int): Float = paddingLeft + (width - paddingLeft - paddingRight) * second / DAY.toFloat()

    override fun onDraw(canvas: Canvas) {
        val band = height - label.textSize - 10 * density
        val radius = 3 * density
        val sorted = seconds.sorted()
        val gaps = sorted.zipWithNext()
        gaps.filter { (a, b) -> b - a >= HOUR }.forEach { (a, b) -> canvas.drawRoundRect(x(a), 0f, x(b), band, radius, radius, shade) }
        gaps.maxByOrNull { (a, b) -> b - a }?.let { (a, b) -> canvas.drawRoundRect(x(a), 0f, x(b), band, radius, radius, outline) }
        seconds.forEachIndexed { i, s ->
            tick.color = context.getColor(if (i in lit) R.color.accent else R.color.text)
            canvas.drawRect(x(s) - 0.75f * density, 2 * density, x(s) + 0.75f * density, band - 2 * density, tick)
        }
        for (hour in 0..24 step 6) {
            val ax = x(hour * HOUR)
            canvas.drawRect(ax - density / 2, band + 2 * density, ax + density / 2, band + 6 * density, axis)
            val text = "%02d:00".format(hour)
            label.textAlign = when (hour) {
                0 -> Paint.Align.LEFT
                24 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(text, ax, height.toFloat() - 2 * density, label)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onTap == null || seconds.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressed = true
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) >= slop || abs(event.y - downY) >= slop) pressed = false
            MotionEvent.ACTION_CANCEL -> pressed = false
            MotionEvent.ACTION_UP -> if (pressed) {
                val second = ((event.x - paddingLeft) / (width - paddingLeft - paddingRight) * DAY).toInt()
                tapped = seconds.indices.minBy { abs(seconds[it] - second) }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val index = tapped ?: 0
        tapped = null
        if (seconds.isNotEmpty()) onTap?.invoke(index)
        return true
    }

    private companion object {
        const val HOUR = 3600
        const val DAY = 24 * HOUR
    }
}
