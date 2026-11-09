package com.alexamy.unlockcounter

import android.content.Context
import java.io.OutputStream
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Today's unlock count, the daily history and the log of unlock times.
 * The count of a finished day moves to history at the first read or unlock of another day.
 * History is never overwritten: a day seen again (clock or time zone moved back) continues from its stored count.
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
        storage.write(dayChanges(current.copy(count = current.count + 1)) + (LAST to time.toString()))
        storage.appendLog("$time\n")
    }

    /**
     * Counts the first unlock after a boot, which happens before the wallpaper is running,
     * when an earlier unlock is recorded but none since [boot]. Without any record (fresh install) nothing is counted.
     */
    fun addUnlockIfNoneSince(boot: OffsetDateTime) {
        val last = storage[LAST]?.let(OffsetDateTime::parse) ?: return
        if (last.isBefore(boot)) addUnlock()
    }

    /** Past days from yesterday back to the oldest record, newest first; null count = no data that day. */
    fun historyRows(): List<Pair<LocalDate, Int?>> {
        val today = rollover(now().toLocalDate()).day
        val records = history()
        val oldest = records.keys.minOrNull() ?: return emptyList()
        return generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .takeWhile { it >= oldest }
            .map { it to records[it] }
            .toList()
    }

    /** The last [days] days ending today, oldest first; today is the current count, null = no data that day. */
    fun lastDays(days: Int): List<Pair<LocalDate, Int?>> {
        val today = rollover(now().toLocalDate())
        val records = history()
        return (days - 1 downTo 0).map { back ->
            val day = today.day.minusDays(back.toLong())
            day to if (back == 0) today.count else records[day]
        }
    }

    /** Average over the finished days with data among the last [days] days; null when there are none. */
    fun dailyAverage(days: Int): Double? =
        lastDays(days).dropLast(1).mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()

    fun writeLog(out: OutputStream) = storage.writeLog(out)

    fun logEntries(): Int = storage.logLines()

    fun clearLog() = storage.clearLog()

    private fun history(): Map<LocalDate, Int> = storage.all()
        .filterKeys { it.startsWith(HISTORY) }
        .map { (key, value) -> LocalDate.parse(key.removePrefix(HISTORY)) to value.toInt() }
        .toMap()

    private data class DayCount(val day: LocalDate, val count: Int)

    private fun rollover(today: LocalDate): DayCount {
        val stored = DayCount(storage[DAY]?.let(LocalDate::parse) ?: today, storage[COUNT]?.toInt() ?: 0)
        // Before network time sync a phone can boot at 1970: keep counting on the stored day.
        if (stored.day == today || today < MIN_DAY) return stored
        val archived = (storage[HISTORY + stored.day]?.toInt() ?: 0) + stored.count
        val current = DayCount(today, storage[HISTORY + today]?.toInt() ?: 0)
        storage.write(dayChanges(current) + mapOf(HISTORY + stored.day to archived.toString(), HISTORY + today to null))
        return current
    }

    private fun dayChanges(value: DayCount): Map<String, String?> =
        mapOf(DAY to value.day.toString(), COUNT to value.count.toString())

    private companion object {
        const val DAY = "day"
        const val COUNT = "count"
        const val HISTORY = "history:"
        const val LAST = "last"
        val MIN_DAY: LocalDate = LocalDate.of(2020, 1, 1)
    }
}
