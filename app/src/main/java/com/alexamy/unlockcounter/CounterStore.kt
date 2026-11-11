package com.alexamy.unlockcounter

import android.content.Context
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters

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

    fun todayCount(): Int = rollover(dayOf(now())).count

    fun addUnlock() {
        val time = now()
        val current = rollover(dayOf(time))
        storage.write(dayChanges(current.copy(count = current.count + 1)) + (LAST to time.toString()))
        storage.appendLog(LogEvent(time, isLock = false).line)
    }

    /** Logs a lock, once per unlock: a screen turned on and off again without an unlock adds nothing. */
    fun addLock() {
        val unlock = storage[LAST]?.let(OffsetDateTime::parse) ?: return
        val lock = storage[LAST_LOCK]?.let(OffsetDateTime::parse)
        if (lock != null && !lock.isBefore(unlock)) return
        val time = now()
        // Before clock sync the day is 1970: wait for a real day to mark when locks start.
        val since = storage[LOCKS_SINCE] ?: dayOf(time).takeIf { it >= MIN_DAY }?.toString()
        storage.write(mapOf(LAST_LOCK to time.toString(), LOCKS_SINCE to since))
        storage.appendLog(LogEvent(time, isLock = true).line)
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
        val today = rollover(dayOf(now())).day
        val records = history()
        val oldest = records.keys.minOrNull() ?: return emptyList()
        return generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .takeWhile { it >= oldest }
            .map { it to records[it] }
            .toList()
    }

    /** Monday-to-Sunday weeks that have data, oldest first; the last one is the current week. */
    fun weeks(): List<Week> {
        val today = rollover(dayOf(now()))
        val records = history() + (today.day to today.count)
        val current = today.day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return records.keys.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .filter { it <= current }
            .distinct()
            .sorted()
            .map { monday ->
                val days = (0L..6L).map(monday::plusDays).filter { it <= today.day }.map { it to records[it] }
                val finished = days.filter { it.first < today.day }.mapNotNull { it.second }
                Week(monday, days, finished.takeIf { it.isNotEmpty() }?.average())
            }
    }

    /**
     * [date] with its count and logged unlock times. "Today" is the store's current day, also before a clock sync.
     * Each log line belongs to the day at the offset it was recorded with, by [dayStart]; unreadable lines are skipped.
     */
    fun day(date: LocalDate): Day {
        val start = dayStart
        val today = rollover(dayOf(now(), start))
        val isToday = date == today.day
        // Count before log: an unlock recorded in between then adds a time, never a false "missing".
        val count = if (isToday) today.count else history()[date]
        val prefixes = (0L..2L).map { date.plusDays(it).toString() }
        val events = storage.readLog { lines -> lines.filter { line -> prefixes.any(line::startsWith) }.mapNotNull(LogEvent::parse).toList() }
        return Day(
            date, count, events, isToday, this[DurationSetting.BURST], start,
            glance = this[DurationSetting.GLANCE],
            now = if (isToday) now() else null,
            locksSince = storage[LOCKS_SINCE]?.let(LocalDate::parse),
            openLimit = this[DurationSetting.OPEN_LIMIT],
        )
    }

    operator fun get(setting: DurationSetting): Duration =
        storage[setting.key]?.toLongOrNull()?.takeIf { it in setting.range }?.let(setting::of) ?: setting.default

    operator fun set(setting: DurationSetting, value: Duration) =
        storage.write(mapOf(setting.key to setting.units(value).toString()))

    /** When a day begins; an unlock before it counts on the day before. */
    var dayStart: LocalTime
        get() = storage[DAY_START]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: Day.DEFAULT_START
        set(value) {
            // The current day keeps counting until the new start, so a later start never reopens yesterday.
            val current = rollover(dayOf(now()))
            storage.write(mapOf(DAY_START to value.toString(), HELD_DAY to current.day.toString()))
        }

    private fun dayOf(time: OffsetDateTime, start: LocalTime = dayStart): LocalDate = Day.dayOf(time, start)

    fun writeLog(out: OutputStream) = storage.writeLog(out)

    fun logEntries(): Int = storage.readLog { lines -> lines.count { LogEvent.parse(it)?.isLock != true } }

    fun clearLog() {
        storage.clearLog()
        storage.write(mapOf(LAST_LOCK to null, LOCKS_SINCE to null))
    }

    private fun history(): Map<LocalDate, Int> = storage.all()
        .filterKeys { it.startsWith(HISTORY) }
        .map { (key, value) -> LocalDate.parse(key.removePrefix(HISTORY)) to value.toInt() }
        .toMap()

    private data class DayCount(val day: LocalDate, val count: Int)

    private fun rollover(today: LocalDate): DayCount {
        val stored = DayCount(storage[DAY]?.let(LocalDate::parse) ?: today, storage[COUNT]?.toInt() ?: 0)
        // Before network time sync a phone can boot at 1970: keep counting on the stored day.
        if (stored.day == today || today < MIN_DAY) return stored
        if (today < stored.day && storage[HELD_DAY] == stored.day.toString()) return stored
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
        const val DAY_START = "day_start"
        const val HELD_DAY = "held_day"
        const val LAST_LOCK = "last_lock"
        const val LOCKS_SINCE = "locks_since"
        val MIN_DAY: LocalDate = LocalDate.of(2020, 1, 1)
    }
}
