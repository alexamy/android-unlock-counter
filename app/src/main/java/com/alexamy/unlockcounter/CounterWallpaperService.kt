package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.OffsetDateTime

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val store by lazy { CounterStore(this) }
    private val rendererLazy = lazy { WallpaperRenderer(this) { redrawVisible() } }
    private val renderer by rendererLazy

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> store.addUnlock()
                Intent.ACTION_SCREEN_OFF -> store.addLock()
            }
            redrawVisible()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
            // Each minute and on clock changes: a day can begin at any minute (day start setting), or the setting can change.
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(receiver, filter)
        // The service starts a few seconds after the first unlock of a boot, too late to receive it.
        // shortcut: also counts one unlock when the wallpaper is set again after a boot with no unlock since, rare.
        store.addUnlockIfNoneSince(OffsetDateTime.now().minusNanos(SystemClock.elapsedRealtimeNanos()))
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
