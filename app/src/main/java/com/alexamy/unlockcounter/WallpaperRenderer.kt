package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/** Draws the wallpaper; shared by the live wallpaper and the settings preview. */
class WallpaperRenderer(private val context: Context) {
    val style = StyleStore(context)
    private val typefaces = mutableMapOf<NumberFont, Typeface>()
    private var background: Bitmap? = style.loadImage()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = Rect()

    fun reloadImage() {
        background = style.loadImage()
    }

    fun draw(canvas: Canvas, width: Int, height: Int, count: Int) {
        canvas.drawColor(BACKGROUND)
        background?.let {
            val (l, t, r, b) = cropToFill(it.width, it.height, width, height, style.imageX)
            canvas.drawBitmap(it, Rect(l, t, r, b), Rect(0, 0, width, height), imagePaint)
        }
        paint.color = style.color
        paint.typeface = typefaces.getOrPut(style.font) { style.font.load(context.assets) }
        // Size and anchor from "888", so the number does not jump when digits change.
        paint.textSize = 100f
        paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
        val fitWidth = width * (1 - 2 * MARGIN) / bounds.width()
        val fitHeight = height / 2f * (1 - 2 * MARGIN) / bounds.height()
        paint.textSize = 100f * minOf(fitWidth, fitHeight) * style.size
        paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
        val column = style.position % 3
        val row = style.position / 3
        paint.textAlign = ALIGNS[column]
        val x = when (column) {
            0 -> width * MARGIN
            1 -> width / 2f
            else -> width * (1 - MARGIN)
        }
        val baseline = when (row) {
            0 -> height * MARGIN - bounds.top
            1 -> height / 2f - bounds.exactCenterY()
            else -> height * (1 - MARGIN) - bounds.bottom
        }
        canvas.drawText(count.toString(), x, baseline, paint)
    }

    private companion object {
        val BACKGROUND = Color.rgb(0x42, 0x42, 0x42)
        const val WIDEST = "888"
        const val MARGIN = 0.05f
        val ALIGNS = arrayOf(Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.RIGHT)
    }
}
