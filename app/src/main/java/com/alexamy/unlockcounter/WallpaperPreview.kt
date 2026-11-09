package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager

/** Scaled-down wallpaper with the screen's aspect ratio, PREVIEW_HEIGHT of the screen tall. */
class WallpaperPreview(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    val renderer = WallpaperRenderer(context)
    private val store = CounterStore(context)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (screenW, screenH) = screenSize()
        val h = (screenH * PREVIEW_HEIGHT).toInt()
        setMeasuredDimension(h * screenW / screenH, h)
    }

    override fun onDraw(canvas: Canvas) = renderer.draw(canvas, width, height, store.today().count)

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

    private companion object {
        const val PREVIEW_HEIGHT = 0.35f
    }
}
