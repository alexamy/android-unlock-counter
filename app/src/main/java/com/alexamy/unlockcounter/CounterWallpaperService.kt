package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val store by lazy { CounterStore(this) }

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            store.addUnlock()
            engines.forEach { it.draw() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = CounterEngine()

    private companion object {
        val BACKGROUND = Color.rgb(0x42, 0x42, 0x42)
        val FONT = NumberFont.SIX_CAPS
        const val WIDEST = "888"
        const val MARGIN = 0.05f
    }

    inner class CounterEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = FONT.load(assets)
        }
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
                // Size and center from "888", so the number does not jump when digits change.
                paint.textSize = 100f
                paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
                val fitWidth = canvas.width * (1 - 2 * MARGIN) / bounds.width()
                val fitHeight = canvas.height / 2f * (1 - 2 * MARGIN) / bounds.height()
                paint.textSize = 100f * minOf(fitWidth, fitHeight)
                paint.getTextBounds(WIDEST, 0, WIDEST.length, bounds)
                val baseline = canvas.height / 2f - bounds.exactCenterY()
                canvas.drawText(count.toString(), canvas.width / 2f, baseline, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
