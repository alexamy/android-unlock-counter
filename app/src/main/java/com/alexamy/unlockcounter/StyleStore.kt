package com.alexamy.unlockcounter

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

class StyleStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("style", Context.MODE_PRIVATE)
    private val imageFile = File(context.filesDir, "background.jpg")

    var color: Int
        get() = prefs.getInt(COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(COLOR, value).apply()

    var opacity: Float
        get() = prefs.getFloat(OPACITY, 1f)
        set(value) = prefs.edit().putFloat(OPACITY, value.coerceIn(MIN_OPACITY, 1f)).apply()

    /** Slider level 0..1 for [opacity]. */
    var opacityLevel: Float
        get() = (opacity - MIN_OPACITY) / (1 - MIN_OPACITY)
        set(value) {
            opacity = MIN_OPACITY + value.coerceIn(0f, 1f) * (1 - MIN_OPACITY)
        }

    var font: NumberFont
        get() = NumberFont.entries.find { it.name == prefs.getString(FONT, null) } ?: NumberFont.SIX_CAPS
        set(value) = prefs.edit().putString(FONT, value.name).apply()

    /** 0.15..1 of the size that fits "888". */
    var size: Float
        get() = prefs.getFloat(SIZE, 1f)
        set(value) = prefs.edit().putFloat(SIZE, value.coerceIn(MIN_SIZE, 1f)).apply()

    /** Slider level 0..1 for [size]. */
    var sizeLevel: Float
        get() = (size - MIN_SIZE) / (1 - MIN_SIZE)
        set(value) {
            size = MIN_SIZE + value.coerceIn(0f, 1f) * (1 - MIN_SIZE)
        }

    /** 0..8, row by row from top-left; 4 = center. */
    var position: Int
        get() = prefs.getInt(POSITION, 4)
        set(value) = prefs.edit().putInt(POSITION, value.coerceIn(0, 8)).apply()

    /** Horizontal crop of a wide image: 0 = left edge, 1 = right edge. */
    var imageX: Float
        get() = prefs.getFloat(IMAGE_X, 0.5f)
        set(value) = prefs.edit().putFloat(IMAGE_X, value.coerceIn(0f, 1f)).apply()

    /** Null when there is no image or it cannot be decoded (also on out-of-memory), so the wallpaper never crashes on it. */
    fun loadImage(): Bitmap? = if (imageFile.exists()) runCatching { BitmapFactory.decodeFile(imageFile.path) }.getOrNull() else null

    /** Copies the picked image, scaled down to about [screenW] x [screenH], so the original can be deleted. */
    fun saveImage(uri: Uri, screenW: Int, screenH: Int) = synchronized(FILE_LOCK) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = decodeScale(w, h, screenW, screenH)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val scale = decodeScale(bounds.outWidth, bounds.outHeight, screenW, screenH)
            var sample = 1
            while (sample * 2 * scale <= 1f) sample *= 2
            // shortcut: ignores EXIF rotation on Android 8.x, use ExifInterface if a user there reports it.
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } ?: error("Cannot decode $uri")
        val tmp = File(context.filesDir, "background.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        check(tmp.renameTo(imageFile)) { "Cannot save image" }
        prefs.edit().putLong(IMAGE, System.currentTimeMillis()).apply()
    }

    /**
     * Calls [onChange] after every settings change, with true when the image changed.
     * Returns the function that stops watching; keep it, the listener lives as long as it does.
     */
    fun watch(onChange: (imageChanged: Boolean) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onChange(key == null || key == IMAGE) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Back to the default look: no image, default color, opacity, font, size, position. */
    fun reset() {
        removeImage()
        prefs.edit().clear().apply()
    }

    fun removeImage() = synchronized(FILE_LOCK) {
        imageFile.delete()
        prefs.edit().remove(IMAGE).apply()
    }

    private companion object {
        const val IMAGE = "image"
        // Image saves run on background threads; one at a time, across all StyleStore instances.
        val FILE_LOCK = Any()
        const val MIN_SIZE = 0.15f
        const val MIN_OPACITY = 0.1f
        private const val COLOR = "color"
        private const val OPACITY = "opacity"
        private const val FONT = "font"
        private const val SIZE = "size"
        private const val POSITION = "position"
        private const val IMAGE_X = "imageX"
    }
}
