package com.alexamy.unlockcounter

import android.content.Context
import java.io.File
import java.io.OutputStream

/** Where CounterStore keeps its values and the unlock-time log. */
interface CounterStorage {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String)
    fun keys(): Set<String>
    fun appendLog(text: String)
    fun writeLog(out: OutputStream)
    fun clearLog()
}

class PrefsCounterStorage(context: Context) : CounterStorage {
    private val prefs = context.getSharedPreferences("counter", Context.MODE_PRIVATE)
    private val log = File(context.filesDir, "unlocks.log")

    // toString: older versions stored count and history as Int.
    override fun get(key: String) = prefs.all[key]?.toString()

    override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()

    override fun keys(): Set<String> = prefs.all.keys

    override fun appendLog(text: String) = log.appendText(text)

    override fun writeLog(out: OutputStream) {
        if (log.exists()) log.inputStream().use { it.copyTo(out) }
    }

    override fun clearLog() {
        log.delete()
    }
}
