package com.alexamy.unlockcounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.min

class StyleStore(private val context: Context) {
    val prefs = context.getSharedPreferences("style", Context.MODE_PRIVATE)
    private val imageFile = File(context.filesDir, "background.jpg")

    var color: Int
        get() = prefs.getInt(COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(COLOR, value).apply()

    var font: NumberFont
        get() = NumberFont.entries.find { it.name == prefs.getString(FONT, null) } ?: NumberFont.SIX_CAPS
        set(value) = prefs.edit().putString(FONT, value.name).apply()

    /** 0.15..1 of the size that fits "888". */
    var size: Float
        get() = prefs.getFloat(SIZE, 1f)
        set(value) = prefs.edit().putFloat(SIZE, value.coerceIn(MIN_SIZE, 1f)).apply()

    /** 0..8, row by row from top-left; 4 = center. */
    var position: Int
        get() = prefs.getInt(POSITION, 4)
        set(value) = prefs.edit().putInt(POSITION, value.coerceIn(0, 8)).apply()

    fun loadImage(): Bitmap? = if (imageFile.exists()) BitmapFactory.decodeFile(imageFile.path) else null

    /** Copies the picked image, scaled down to about [screenW] x [screenH], so the original can be deleted. */
    fun saveImage(uri: Uri, screenW: Int, screenH: Int) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = min(1f, max(min(screenW, screenH).toFloat() / min(w, h), max(screenW, screenH).toFloat() / max(w, h)))
                decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // shortcut: ignores EXIF rotation on Android 8.x, use ExifInterface if a user there reports it.
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        } ?: error("Cannot decode $uri")
        val tmp = File(context.filesDir, "background.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        check(tmp.renameTo(imageFile)) { "Cannot save image" }
        prefs.edit().putLong(IMAGE, System.currentTimeMillis()).apply()
    }

    fun removeImage() {
        imageFile.delete()
        prefs.edit().remove(IMAGE).apply()
    }

    companion object {
        const val IMAGE = "image"
        const val MIN_SIZE = 0.15f
        private const val COLOR = "color"
        private const val FONT = "font"
        private const val SIZE = "size"
        private const val POSITION = "position"
    }
}
