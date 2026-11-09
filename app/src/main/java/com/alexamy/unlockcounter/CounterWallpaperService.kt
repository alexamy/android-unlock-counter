package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val store by lazy { CounterStore(this) }
    private val renderer by lazy { WallpaperRenderer(this) }

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            store.addUnlock()
            engines.forEach { it.draw() }
        }
    }

    // Kept in a field: SharedPreferences holds listeners weakly.
    private val styleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StyleStore.IMAGE) renderer.reloadImage()
        engines.forEach { it.draw() }
    }

    override fun onCreate() {
        super.onCreate()
        renderer.style.prefs.registerOnSharedPreferenceChangeListener(styleListener)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        renderer.style.prefs.unregisterOnSharedPreferenceChangeListener(styleListener)
        super.onDestroy()
    }

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
                renderer.draw(canvas, canvas.width, canvas.height, store.today().count)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
