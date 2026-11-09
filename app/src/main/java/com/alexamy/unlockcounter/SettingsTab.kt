package com.alexamy.unlockcounter

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import kotlin.math.roundToInt

/** Binds the style controls of the Settings tab to [style]. */
class SettingsTab(private val activity: Activity, private val style: StyleStore, pickImage: () -> Unit) {
    private val hsv = FloatArray(3)

    init {
        view<View>(R.id.pick_image).setOnClickListener { pickImage() }
        view<View>(R.id.remove_image).setOnClickListener { style.removeImage() }

        val imageX = view<SeekBar>(R.id.image_x)
        imageX.progress = (style.imageX * 100).roundToInt()
        imageX.setOnSeekBarChangeListener(onUserChange { style.imageX = imageX.progress / 100f })

        val swatch = view<View>(R.id.color_preview)
        val hue = view<SeekBar>(R.id.hue)
        val saturation = view<SeekBar>(R.id.saturation)
        val brightness = view<SeekBar>(R.id.brightness)
        Color.colorToHSV(style.color, hsv)
        hue.progress = hsv[0].roundToInt()
        saturation.progress = (hsv[1] * 100).roundToInt()
        brightness.progress = (hsv[2] * 100).roundToInt()
        swatch.setBackgroundColor(style.color)
        val onColor = onUserChange {
            hsv[0] = hue.progress.toFloat()
            hsv[1] = saturation.progress / 100f
            hsv[2] = brightness.progress / 100f
            style.color = Color.HSVToColor(hsv)
            swatch.setBackgroundColor(style.color)
        }
        listOf(hue, saturation, brightness).forEach { it.setOnSeekBarChangeListener(onColor) }

        val opacity = view<SeekBar>(R.id.opacity)
        opacity.progress = ((style.opacity - StyleStore.MIN_OPACITY) * 100).roundToInt()
        opacity.setOnSeekBarChangeListener(onUserChange { style.opacity = StyleStore.MIN_OPACITY + opacity.progress / 100f })

        val fonts = view<RadioGroup>(R.id.fonts)
        NumberFont.entries.forEach { font ->
            fonts.addView(RadioButton(activity).apply {
                id = View.generateViewId()
                tag = font
                text = activity.getString(R.string.font_sample, font.label)
                typeface = font.load(activity.assets)
                textSize = 28f
                isChecked = font == style.font
            })
        }
        fonts.setOnCheckedChangeListener { group, id -> style.font = group.findViewById<View>(id).tag as NumberFont }

        val positions = view<GridLayout>(R.id.positions)
        POSITION_ARROWS.forEachIndexed { index, arrow ->
            positions.addView(Button(activity).apply {
                text = arrow
                setOnClickListener { style.position = index; markPosition(positions) }
            })
        }
        markPosition(positions)

        val size = view<SeekBar>(R.id.size)
        size.progress = ((style.size - StyleStore.MIN_SIZE) * 100).roundToInt()
        size.setOnSeekBarChangeListener(onUserChange { style.size = StyleStore.MIN_SIZE + size.progress / 100f })
    }

    private fun <T : View> view(id: Int): T = activity.findViewById(id)

    private fun markPosition(grid: GridLayout) {
        for (i in 0 until grid.childCount) grid.getChildAt(i).alpha = if (i == style.position) 1f else 0.4f
    }

    private fun onUserChange(block: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) block()
        }

        override fun onStartTrackingTouch(bar: SeekBar) = Unit
        override fun onStopTrackingTouch(bar: SeekBar) = Unit
    }

    private companion object {
        val POSITION_ARROWS = listOf("↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘")
    }
}
