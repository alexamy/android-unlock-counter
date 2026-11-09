package com.alexamy.unlockcounter

import android.graphics.Color
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CounterWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CounterEngine()

    inner class CounterEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.BLACK)
                paint.textSize = canvas.width * 0.4f
                canvas.drawText("42", canvas.width / 2f, canvas.height * 0.6f, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
