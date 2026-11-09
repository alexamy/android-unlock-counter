package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val store by lazy { CounterStore(this) }
    private val rendererLazy = lazy { WallpaperRenderer(this) { redrawVisible() } }
    private val renderer by rendererLazy

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) store.addUnlock()
            redrawVisible()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
            // Midnight and clock changes: the shown day may change without an unlock.
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        if (rendererLazy.isInitialized()) renderer.close()
        super.onDestroy()
    }

    // Hidden engines redraw in onVisibilityChanged; drawing them now only costs main-thread time.
    private fun redrawVisible() = engines.filter { it.isVisible }.forEach { it.draw() }

    override fun onCreateEngine(): Engine = CounterEngine()

    inner class CounterEngine : Engine() {
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
                renderer.draw(canvas, canvas.width, canvas.height, store.todayCount())
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
