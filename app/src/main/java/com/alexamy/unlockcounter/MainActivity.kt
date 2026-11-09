package com.alexamy.unlockcounter

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM")
    private lateinit var renderer: WallpaperRenderer
    private lateinit var preview: WallpaperPreview
    private lateinit var tabs: List<View>
    private lateinit var tabButtons: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        renderer = WallpaperRenderer(this) { preview.invalidate() }
        preview = findViewById(R.id.settings_preview)
        preview.renderer = renderer

        tabs = listOf(R.id.tab_history, R.id.tab_settings, R.id.tab_system).map { findViewById(it) }
        tabButtons = listOf(R.id.show_history, R.id.show_settings, R.id.show_system).map { findViewById(it) }
        tabButtons.forEachIndexed { index, button -> button.setOnClickListener { showTab(index) } }
        findViewById<TextView>(R.id.today_count).typeface = NumberFont.SIX_CAPS.load(assets)
        findViewById<View>(R.id.set_wallpaper).setOnClickListener { openWallpaperPicker() }
        SettingsTab(this, renderer.style) { startActivityForResult(pickImageIntent(), PICK_IMAGE) }
        findViewById<View>(R.id.export_log).setOnClickListener { startActivityForResult(exportLogIntent(), EXPORT_LOG) }
        findViewById<View>(R.id.clear_log).setOnClickListener {
            confirm(R.string.clear_log_confirm) {
                CounterStore(this).clearLog()
                showLogSize()
                toast(R.string.log_cleared)
            }
        }
        findViewById<View>(R.id.reset_settings).setOnClickListener {
            confirm(R.string.reset_settings_confirm) {
                renderer.style.reset()
                // Rebuild so the Settings controls show the default values.
                intent.putExtra(EXTRA_TAB, SYSTEM)
                recreate()
            }
        }

        showTab(startTab(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showTab(startTab(intent))
    }

    private fun startTab(intent: Intent) = intent.getIntExtra(EXTRA_TAB, HISTORY)

    override fun onResume() {
        super.onResume()
        showHistory()
        showLogSize()
        preview.invalidate()
    }

    private fun showLogSize() {
        findViewById<TextView>(R.id.export_subtitle).text = getString(R.string.export_subtitle, CounterStore(this).logEntries())
    }

    private fun showHistory() {
        val store = CounterStore(this)
        val todayCount = store.todayCount()
        val rows = store.historyRows()
        val today = LocalDate.now()
        val counts = rows.toMap()
        findViewById<TextView>(R.id.today_count).text = todayCount.toString()
        findViewById<TextView>(R.id.average).text =
            rows.take(BAR_DAYS).mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—"
        findViewById<BarChart>(R.id.bars).values = (BAR_DAYS - 1 downTo 1).map { counts[today.minusDays(it.toLong())] } + todayCount
        findViewById<TextView>(R.id.bars_start).text = today.minusDays(BAR_DAYS - 1L).format(shortDate)
        findViewById<ListView>(R.id.history).adapter = object : ArrayAdapter<Pair<LocalDate, Int?>>(this, 0, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.row_history, parent, false)
                val (day, count) = rows[position]
                row.findViewById<TextView>(R.id.day).text = day.format(dateFormat)
                row.findViewById<TextView>(R.id.count).apply {
                    text = count?.toString() ?: "—"
                    setTextColor(getColor(if (count == null) R.color.text_muted else R.color.text))
                }
                return row
            }
        }
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
                    runCatching { renderer.style.saveImage(uri, metrics.widthPixels, metrics.heightPixels) }
                        .onFailure { runOnUiThread { toast(R.string.image_failed) } }
                }
            }
            EXPORT_LOG -> {
                runCatching {
                    contentResolver.openOutputStream(uri).use { CounterStore(this).writeLog(checkNotNull(it)) }
                }.fold({ toast(R.string.log_exported) }, { toast(R.string.export_failed) })
            }
        }
    }

    private fun confirm(message: Int, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun exportLogIntent() = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TITLE, "unlocks.log")

    private fun showTab(index: Int) {
        tabs.forEachIndexed { i, tab -> tab.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabButtons.forEachIndexed { i, button ->
            val selected = i == index
            button.isSelected = selected
            button.setBackgroundResource(if (selected) R.drawable.bg_segment_selected else 0)
            button.setTextColor(getColor(if (selected) R.color.background else R.color.text_muted))
            button.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
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
        private const val BAR_DAYS = 14
        const val HISTORY = 0
        const val SETTINGS = 1
        const val SYSTEM = 2
        private const val PICK_IMAGE = 1
        private const val EXPORT_LOG = 2
    }
}
