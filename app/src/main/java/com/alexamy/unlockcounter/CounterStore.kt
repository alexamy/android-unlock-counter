package com.alexamy.unlockcounter

import android.content.Context
import java.time.LocalDate

class CounterStore(context: Context) {
    private val prefs = context.getSharedPreferences("counter", Context.MODE_PRIVATE)

    fun today(now: LocalDate = LocalDate.now()): DayCount {
        val stored = load(now)
        stored.finishedBefore(now)?.let { finished ->
            prefs.edit().putInt(HISTORY + finished.day, finished.count).apply()
            save(stored.on(now))
        }
        return stored.on(now)
    }

    fun addUnlock(now: LocalDate = LocalDate.now()) = save(today(now).plusUnlock(now))

    fun history(): Map<LocalDate, Int> = prefs.all
        .filterKeys { it.startsWith(HISTORY) }
        .map { (key, value) -> LocalDate.parse(key.removePrefix(HISTORY)) to value as Int }
        .toMap()

    private fun load(now: LocalDate): DayCount {
        val day = prefs.getString("day", null)?.let(LocalDate::parse) ?: now
        return DayCount(day, prefs.getInt("count", 0))
    }

    private fun save(value: DayCount) {
        prefs.edit().putString("day", value.day.toString()).putInt("count", value.count).apply()
    }

    private companion object {
        const val HISTORY = "history:"
    }
}
