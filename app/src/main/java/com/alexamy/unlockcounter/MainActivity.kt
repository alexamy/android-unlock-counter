package com.alexamy.unlockcounter

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import kotlin.concurrent.thread

/** The app screen: three tabs, plus the activity results for image picking and log export. */
class MainActivity : Activity() {
    private val store by lazy { CounterStore(this) }
    private val style by lazy { StyleStore(this) }
    private lateinit var renderer: WallpaperRenderer
    private lateinit var preview: WallpaperPreview
    private lateinit var tabs: List<View>
    private lateinit var tabButtons: List<View>
    private lateinit var historyTab: HistoryTab
    private lateinit var settingsTab: SettingsTab
    private lateinit var systemTab: SystemTab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).padForSystemBars()
        renderer = WallpaperRenderer(this) {
            preview.invalidate()
            settingsTab.updateImageControls()
        }
        preview = findViewById(R.id.settings_preview)
        preview.renderer = renderer

        tabs = listOf(R.id.tab_history, R.id.tab_settings, R.id.tab_system).map { findViewById(it) }
        tabButtons = listOf(R.id.show_history, R.id.show_settings, R.id.show_system).map { findViewById(it) }
        tabButtons.forEachIndexed { index, button -> button.setOnClickListener { showTab(index) } }
        findViewById<View>(R.id.set_wallpaper).setOnClickListener { openWallpaperPicker() }

        historyTab = HistoryTab(this, store)
        settingsTab = SettingsTab(this, style) { startActivityForResult(pickImageIntent(), PICK_IMAGE) }
        systemTab = SystemTab(this, store, style, exportLog = { startActivityForResult(exportLogIntent(), EXPORT_LOG) }) {
            settingsTab.refresh()
        }

        showTab(startTab(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showTab(startTab(intent))
    }

    override fun onResume() {
        super.onResume()
        historyTab.refresh()
        systemTab.refresh()
        preview.count = store.todayCount()
    }

    override fun onDestroy() {
        renderer.close()
        super.onDestroy()
    }

    @Deprecated("Framework Activity has no ActivityResult API without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            PICK_IMAGE -> {
                val metrics = resources.displayMetrics
                thread {
                    runCatching { style.saveImage(uri, metrics.widthPixels, metrics.heightPixels) }
                        .onFailure { runOnUiThread { toast(R.string.image_failed) } }
                }
            }
            EXPORT_LOG -> {
                runCatching {
                    contentResolver.openOutputStream(uri).use { store.writeLog(checkNotNull(it)) }
                }.fold({ toast(R.string.log_exported) }, { toast(R.string.export_failed) })
            }
        }
    }

    private fun startTab(intent: Intent) = intent.getIntExtra(EXTRA_TAB, HISTORY).coerceIn(HISTORY, SYSTEM)

    private fun showTab(index: Int) {
        tabs.forEachIndexed { i, tab -> tab.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabButtons.forEachIndexed { i, button -> button.isSelected = i == index }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

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

    private fun exportLogIntent() = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TITLE, "unlocks.log")

    companion object {
        const val EXTRA_TAB = "tab"
        const val HISTORY = 0
        const val SETTINGS = 1
        const val SYSTEM = 2
        private const val PICK_IMAGE = 1
        private const val EXPORT_LOG = 2
    }
}

/** Android 15 forces edge-to-edge for targetSdk 35: keep content out of the system bars. */
fun View.padForSystemBars() {
    if (Build.VERSION.SDK_INT < 35) return
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
