package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val store by lazy { CounterStore(this) }
    private val style by lazy { StyleStore(this) }
    private val typefaces = mutableMapOf<NumberFont, Typeface>()
    private var background: Bitmap? = null

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            store.addUnlock()
            engines.forEach { it.draw() }
        }
    }

    // Kept in a field: SharedPreferences holds listeners weakly.
    private val styleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StyleStore.IMAGE) background = style.loadImage()
        engines.forEach { it.draw() }
    }

    override fun onCreate() {
        super.onCreate()
        background = style.loadImage()
        style.prefs.registerOnSharedPreferenceChangeListener(styleListener)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        style.prefs.unregisterOnSharedPreferenceChangeListener(styleListener)
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = CounterEngine()

    private companion object {
        val BACKGROUND = Color.rgb(0x42, 0x42, 0x42)
        const val WIDEST = "888"
        const val MARGIN = 0.05f
        val ALIGNS = arrayOf(Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.RIGHT)
    }

    inner class CounterEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val bounds = Rect()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            engines += this
        }

        override fun onDestroy() {
            engines -= this
            super.onDestroy()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        fun draw() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                val count = store.today().count
                canvas.drawColor(BACKGROUND)
                background?.let {
                    val (l, t, r, b) = centerCrop(it.width, it.height, canvas.width, canvas.height)
                    canvas.drawBitmap(it, Rect(l, t, r, b), Rect(0, 0, canvas.width, canvas.height), imagePaint)
                }
                paint.color = style.color
                paint.typeface = typefaces.getOrPut(style.font) { style.font.load(assets) }
                // Size and center from "888", so the number does not jump when digits change.
                paint.textSize = 100f
                paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
                val fitWidth = canvas.width * (1 - 2 * MARGIN) / bounds.width()
                val fitHeight = canvas.height / 2f * (1 - 2 * MARGIN) / bounds.height()
                paint.textSize = 100f * minOf(fitWidth, fitHeight) * style.size
                paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
                val column = style.position % 3
                val row = style.position / 3
                paint.textAlign = ALIGNS[column]
                val x = when (column) {
                    0 -> canvas.width * MARGIN
                    1 -> canvas.width / 2f
                    else -> canvas.width * (1 - MARGIN)
                }
                val baseline = when (row) {
                    0 -> canvas.height * MARGIN - bounds.top
                    1 -> canvas.height / 2f - bounds.exactCenterY()
                    else -> canvas.height * (1 - MARGIN) - bounds.bottom
                }
                canvas.drawText(count.toString(), x, baseline, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
