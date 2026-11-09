package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class StyleSettingsActivity : Activity() {
    private lateinit var style: StyleStore
    private lateinit var preview: WallpaperPreview
    private val hsv = FloatArray(3)

    // Kept in a field: SharedPreferences holds listeners weakly.
    private val styleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StyleStore.IMAGE) preview.renderer.reloadImage()
        preview.invalidate()
    }

    override fun onDestroy() {
        style.prefs.unregisterOnSharedPreferenceChangeListener(styleListener)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)
        preview = findViewById(R.id.preview)
        style = preview.renderer.style
        style.prefs.registerOnSharedPreferenceChangeListener(styleListener)

        findViewById<View>(R.id.pick_image).setOnClickListener { startActivityForResult(pickImageIntent(), PICK_IMAGE) }
        findViewById<View>(R.id.remove_image).setOnClickListener { style.removeImage() }

        val preview = findViewById<View>(R.id.color_preview)
        val hue = findViewById<SeekBar>(R.id.hue)
        val saturation = findViewById<SeekBar>(R.id.saturation)
        val brightness = findViewById<SeekBar>(R.id.brightness)
        Color.colorToHSV(style.color, hsv)
        hue.progress = hsv[0].roundToInt()
        saturation.progress = (hsv[1] * 100).roundToInt()
        brightness.progress = (hsv[2] * 100).roundToInt()
        preview.setBackgroundColor(style.color)
        val onColor = onUserChange {
            hsv[0] = hue.progress.toFloat()
            hsv[1] = saturation.progress / 100f
            hsv[2] = brightness.progress / 100f
            style.color = Color.HSVToColor(hsv)
            preview.setBackgroundColor(style.color)
        }
        listOf(hue, saturation, brightness).forEach { it.setOnSeekBarChangeListener(onColor) }

        val fonts = findViewById<RadioGroup>(R.id.fonts)
        NumberFont.entries.forEach { font ->
            fonts.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = font
                text = getString(R.string.font_sample, font.label)
                typeface = font.load(assets)
                textSize = 28f
                isChecked = font == style.font
            })
        }
        fonts.setOnCheckedChangeListener { group, id -> style.font = group.findViewById<View>(id).tag as NumberFont }

        val positions = findViewById<GridLayout>(R.id.positions)
        POSITION_ARROWS.forEachIndexed { index, arrow ->
            positions.addView(Button(this).apply {
                text = arrow
                setOnClickListener { style.position = index; markPosition(positions) }
            })
        }
        markPosition(positions)

        val size = findViewById<SeekBar>(R.id.size)
        size.progress = ((style.size - StyleStore.MIN_SIZE) * 100).roundToInt()
        size.setOnSeekBarChangeListener(onUserChange { style.size = StyleStore.MIN_SIZE + size.progress / 100f })
    }

    @Deprecated("Framework Activity has no ActivityResult API without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || uri == null) return
        val metrics = resources.displayMetrics
        thread {
            runCatching { style.saveImage(uri, metrics.widthPixels, metrics.heightPixels) }.onFailure {
                runOnUiThread { Toast.makeText(this, R.string.image_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun markPosition(grid: GridLayout) {
        for (i in 0 until grid.childCount) grid.getChildAt(i).alpha = if (i == style.position) 1f else 0.4f
    }

    private fun pickImageIntent() =
        if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
        else Intent(Intent.ACTION_GET_CONTENT).setType("image/*")

    private fun onUserChange(block: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) block()
        }

        override fun onStartTrackingTouch(bar: SeekBar) = Unit
        override fun onStopTrackingTouch(bar: SeekBar) = Unit
    }

    private companion object {
        const val PICK_IMAGE = 1
        val POSITION_ARROWS = listOf("↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘")
    }
}
