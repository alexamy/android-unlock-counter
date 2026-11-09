package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.LocalDate

class CounterWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<CounterEngine>()
    private val prefs by lazy { getSharedPreferences("counter", MODE_PRIVATE) }

    // One receiver per service, not per engine: the picker preview runs a second engine.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            save(load().plusUnlock(LocalDate.now()))
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

    private fun load(): DayCount {
        val day = prefs.getString("day", null)?.let(LocalDate::parse) ?: LocalDate.now()
        return DayCount(day, prefs.getInt("count", 0))
    }

    private fun save(value: DayCount) {
        prefs.edit().putString("day", value.day.toString()).putInt("count", value.count).apply()
    }

    inner class CounterEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

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
                val count = load().on(LocalDate.now()).count
                canvas.drawColor(Color.BLACK)
                paint.textSize = canvas.width * 0.4f
                canvas.drawText(count.toString(), canvas.width / 2f, canvas.height * 0.6f, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
