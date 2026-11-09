package com.alexamy.unlockcounter

import android.content.Context
import java.io.OutputStream
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Today's unlock count, the daily history and the log of unlock times.
 * The count of a finished day moves to history at the first read or unlock of a new day.
 */
class CounterStore(
    private val storage: CounterStorage,
    private val now: () -> OffsetDateTime = OffsetDateTime::now,
) {
    constructor(context: Context) : this(PrefsCounterStorage(context))

    fun todayCount(): Int = rollover(now().toLocalDate()).count

    fun addUnlock() {
        val time = now()
        val current = rollover(time.toLocalDate())
        save(current.copy(count = current.count + 1))
        storage.appendLog("$time\n")
    }

    /** Past days from yesterday back to the oldest record, newest first; null count = no data that day. */
    fun historyRows(): List<Pair<LocalDate, Int?>> {
        val today = rollover(now().toLocalDate()).day
        val records = storage.keys()
            .filter { it.startsWith(HISTORY) }
            .associate { LocalDate.parse(it.removePrefix(HISTORY)) to storage[it]!!.toInt() }
        val oldest = records.keys.minOrNull() ?: return emptyList()
        return generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .takeWhile { it >= oldest }
            .map { it to records[it] }
            .toList()
    }

    fun writeLog(out: OutputStream) = storage.writeLog(out)

    fun clearLog() = storage.clearLog()

    private data class DayCount(val day: LocalDate, val count: Int)

    private fun rollover(today: LocalDate): DayCount {
        val stored = DayCount(storage[DAY]?.let(LocalDate::parse) ?: today, storage[COUNT]?.toInt() ?: 0)
        if (stored.day == today) return stored
        storage[HISTORY + stored.day] = stored.count.toString()
        return DayCount(today, 0).also(::save)
    }

    private fun save(value: DayCount) {
        storage[DAY] = value.day.toString()
        storage[COUNT] = value.count.toString()
    }

    private companion object {
        const val DAY = "day"
        const val COUNT = "count"
        const val HISTORY = "history:"
    }
}
