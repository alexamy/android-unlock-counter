package com.alexamy.unlockcounter

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.SeekBar
import kotlin.math.roundToInt

/** Binds the style controls of the Settings tab to [style]. */
class SettingsTab(private val activity: Activity, private val style: StyleStore, pickImage: () -> Unit) {
    private val hsv = FloatArray(3)
    private val gap = (activity.resources.displayMetrics.density * 4).toInt()

    private val sections = listOf(R.id.section_image, R.id.section_color, R.id.section_size, R.id.positions, R.id.fonts)
        .map { view<View>(it) }
    private val chips = listOf(R.id.chip_image, R.id.chip_color, R.id.chip_size, R.id.chip_position, R.id.chip_font)
        .map { view<View>(it) }
    private val removeImage = view<View>(R.id.remove_image)
    private val imageX = view<SeekBar>(R.id.image_x)

    init {
        chips.forEachIndexed { index, chip -> chip.setOnClickListener { showSection(index) } }
        showSection(0)

        view<View>(R.id.pick_image).setOnClickListener { pickImage() }
        removeImage.setOnClickListener { style.removeImage() }
        updateImageControls()

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
        hue.progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, HUES).apply {
            cornerRadius = activity.resources.displayMetrics.density * 4
            setSize(0, (activity.resources.displayMetrics.density * 8).toInt())
        }

        val opacity = view<SeekBar>(R.id.opacity)
        opacity.progress = (style.opacityLevel * 100).roundToInt()
        opacity.setOnSeekBarChangeListener(onUserChange { style.opacityLevel = opacity.progress / 100f })

        val fonts = view<GridLayout>(R.id.fonts)
        NumberFont.entries.forEach { font ->
            fonts.addView(Button(activity, null, 0, R.style.Chip).apply {
                text = activity.getString(R.string.font_sample, font.label)
                typeface = font.load(activity.assets)
                textSize = 20f
                setOnClickListener { style.font = font; markSelected(fonts, font.ordinal) }
            }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
                setMargins(0, 0, gap, 0)
            })
        }
        markSelected(fonts, style.font.ordinal)

        val positions = view<GridLayout>(R.id.positions)
        activity.resources.getStringArray(R.array.positions).forEachIndexed { index, name ->
            positions.addView(Button(activity, null, 0, R.style.Spot).apply {
                contentDescription = name
                setOnClickListener { style.position = index; markSelected(positions, index) }
            }, GridLayout.LayoutParams().apply { setMargins(gap, gap, gap, gap) })
        }
        markSelected(positions, style.position)

        val size = view<SeekBar>(R.id.size)
        size.progress = (style.sizeLevel * 100).roundToInt()
        size.setOnSeekBarChangeListener(onUserChange { style.sizeLevel = size.progress / 100f })
    }

    private fun showSection(index: Int) {
        sections.forEachIndexed { i, section -> section.visibility = if (i == index) View.VISIBLE else View.GONE }
        chips.forEachIndexed { i, chip -> chip.isSelected = i == index }
    }

    /** Remove and horizontal position only make sense with a picked image. */
    fun updateImageControls() {
        val hasImage = style.hasImage()
        listOf(removeImage, imageX).forEach {
            it.isEnabled = hasImage
            it.alpha = if (hasImage) 1f else 0.4f
        }
    }

    private fun <T : View> view(id: Int): T = activity.findViewById(id)

    private fun markSelected(grid: GridLayout, index: Int) {
        for (i in 0 until grid.childCount) grid.getChildAt(i).isSelected = i == index
    }

    private fun onUserChange(block: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) block()
        }

        override fun onStartTrackingTouch(bar: SeekBar) = Unit
        override fun onStopTrackingTouch(bar: SeekBar) = Unit
    }

    private companion object {
        val HUES = (0..6).map { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }.toIntArray()
    }
}
