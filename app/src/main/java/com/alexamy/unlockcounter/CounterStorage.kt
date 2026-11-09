package com.alexamy.unlockcounter

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Where CounterStore keeps its values and the unlock-time log. */
interface CounterStorage {
    operator fun get(key: String): String?

    /** Every stored value, read once. */
    fun all(): Map<String, String>

    /** Applies all changes in one write; a null value removes the key. */
    fun write(changes: Map<String, String?>)

    fun appendLog(text: String)
    fun writeLog(out: OutputStream)
    fun logLines(): Int
    fun clearLog()
}

class PrefsCounterStorage(context: Context) : CounterStorage {
    private val prefs = context.getSharedPreferences("counter", Context.MODE_PRIVATE)
    private val log = File(context.filesDir, "unlocks.log")

    // Older versions stored count and history as Int.
    override fun get(key: String): String? =
        try {
            prefs.getString(key, null)
        } catch (e: ClassCastException) {
            prefs.getInt(key, 0).toString()
        }

    override fun all(): Map<String, String> = prefs.all.mapValues { it.value.toString() }

    override fun write(changes: Map<String, String?>) {
        val edit = prefs.edit()
        changes.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
        edit.apply()
    }

    // A full disk must not crash the wallpaper on every unlock; the count is kept, the time line is lost.
    override fun appendLog(text: String) {
        try {
            log.appendText(text)
        } catch (e: IOException) {
            return
        }
    }

    override fun writeLog(out: OutputStream) {
        if (log.exists()) log.inputStream().use { it.copyTo(out) }
    }

    override fun logLines(): Int =
        if (!log.exists()) 0 else log.inputStream().buffered().use { input -> generateSequence { input.read().takeIf { it >= 0 } }.count { it == '\n'.code } }

    override fun clearLog() {
        log.delete()
    }
}
