package com.alexamy.unlockcounter

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private lateinit var renderer: WallpaperRenderer
    private lateinit var preview: WallpaperPreview
    private lateinit var tabs: List<View>
    private lateinit var tabButtons: List<View>

    // Kept in a field: SharedPreferences holds listeners weakly.
    private val styleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StyleStore.IMAGE) renderer.reloadImage()
        preview.invalidate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        renderer = WallpaperRenderer(this)
        preview = findViewById(R.id.settings_preview)
        preview.renderer = renderer
        renderer.style.prefs.registerOnSharedPreferenceChangeListener(styleListener)

        tabs = listOf(R.id.tab_history, R.id.tab_settings).map { findViewById(it) }
        tabButtons = listOf(R.id.show_history, R.id.show_settings).map { findViewById(it) }
        tabButtons.forEachIndexed { index, button -> button.setOnClickListener { showTab(index) } }
        findViewById<View>(R.id.set_wallpaper).setOnClickListener { openWallpaperPicker() }
        SettingsTab(this, renderer.style) { startActivityForResult(pickImageIntent(), PICK_IMAGE) }

        showTab(startTab(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showTab(startTab(intent))
    }

    private fun startTab(intent: Intent) = intent.getIntExtra(EXTRA_TAB, HISTORY)

    override fun onResume() {
        super.onResume()
        val store = CounterStore(this)
        val today = store.today()
        val rows = listOf(getString(R.string.today_row, today.count)) +
            historyRows(store.history(), today.day).map { (day, count) ->
                "${day.format(dateFormat)}    ${count ?: "—"}"
            }
        findViewById<ListView>(R.id.history).adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        preview.invalidate()
    }

    override fun onDestroy() {
        renderer.style.prefs.unregisterOnSharedPreferenceChangeListener(styleListener)
        super.onDestroy()
    }

    @Deprecated("Framework Activity has no ActivityResult API without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || uri == null) return
        val metrics = resources.displayMetrics
        thread {
            runCatching { renderer.style.saveImage(uri, metrics.widthPixels, metrics.heightPixels) }.onFailure {
                runOnUiThread { Toast.makeText(this, R.string.image_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showTab(index: Int) {
        tabs.forEachIndexed { i, tab -> tab.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabButtons.forEachIndexed { i, button -> button.alpha = if (i == index) 1f else 0.5f }
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

    private fun pickImageIntent() =
        if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
        else Intent(Intent.ACTION_GET_CONTENT).setType("image/*")

    companion object {
        const val EXTRA_TAB = "tab"
        const val HISTORY = 0
        const val SETTINGS = 1
        private const val PICK_IMAGE = 1
    }
}
