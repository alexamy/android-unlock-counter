package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager

/** Scaled-down wallpaper: the largest box with the screen's aspect ratio that fits the given space. */
class WallpaperPreview(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    lateinit var renderer: WallpaperRenderer
    var count = 0
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (screenW, screenH) = screenSize()
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        val maxH = MeasureSpec.getSize(heightMeasureSpec)
        if (maxW.toLong() * screenH < maxH.toLong() * screenW) {
            setMeasuredDimension(maxW, (maxW.toLong() * screenH / screenW).toInt())
        } else {
            setMeasuredDimension((maxH.toLong() * screenW / screenH).toInt(), maxH)
        }
    }

    override fun onDraw(canvas: Canvas) = renderer.draw(canvas, width, height, count)

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val windows = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windows.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
        return metrics.widthPixels to metrics.heightPixels
    }
}
