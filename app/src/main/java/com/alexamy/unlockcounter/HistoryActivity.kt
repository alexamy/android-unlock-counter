package com.alexamy.unlockcounter

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HistoryActivity : Activity() {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<Button>(R.id.set_wallpaper).setOnClickListener { openWallpaperPicker() }
    }

    override fun onResume() {
        super.onResume()
        val store = CounterStore(this)
        val today = store.today()
        val rows = listOf(getString(R.string.today_row, today.count)) +
            historyRows(store.history(), today.day).map { (day, count) ->
                "${day.format(dateFormat)}    ${count ?: "—"}"
            }
        findViewById<ListView>(R.id.history).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    private fun openWallpaperPicker() {
        val preview = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, CounterWallpaperService::class.java),
        )
        try {
            startActivity(preview)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
