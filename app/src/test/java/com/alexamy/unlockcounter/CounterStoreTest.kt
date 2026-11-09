package com.alexamy.unlockcounter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CounterStoreTest {
    private class MemoryStorage : CounterStorage {
        val values = mutableMapOf<String, String>()
        val log = StringBuilder()
        override fun get(key: String) = values[key]
        override fun all(): Map<String, String> = values.toMap()
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
        override fun appendLog(text: String) { log.append(text) }
        override fun writeLog(out: OutputStream) = out.write(log.toString().toByteArray())
        override fun logLines() = log.count { it == '\n' }
        override fun clearLog() = log.setLength(0)
    }

    private val storage = MemoryStorage()
    private var time = OffsetDateTime.of(2026, 11, 9, 10, 0, 0, 0, ZoneOffset.ofHours(1))
    private val store = CounterStore(storage) { time }
    private val day = time.toLocalDate()

    private fun nextDay(days: Long = 1) { time = time.plusDays(days) }

    @Test
    fun startsAtZero() = assertEquals(0, store.todayCount())

    @Test
    fun unlocksAddUp() {
        repeat(3) { store.addUnlock() }
        assertEquals(3, store.todayCount())
    }

    @Test
    fun newDayStartsAtZeroAndArchivesYesterday() {
        repeat(5) { store.addUnlock() }
        nextDay()
        assertEquals(0, store.todayCount())
        assertEquals(listOf(day to 5), store.historyRows())
    }

    @Test
    fun firstUnlockOfNewDayIsOne() {
        repeat(5) { store.addUnlock() }
        nextDay()
        store.addUnlock()
        assertEquals(1, store.todayCount())
        assertEquals(listOf(day to 5), store.historyRows())
    }

    @Test
    fun missingDaysAreNullNewestFirst() {
        store.addUnlock()
        nextDay()
        repeat(2) { store.addUnlock() }
        nextDay(3)
        assertEquals(
            listOf(day.plusDays(3) to null, day.plusDays(2) to null, day.plusDays(1) to 2, day to 1),
            store.historyRows(),
        )
    }

    @Test
    fun noHistoryOnFirstDay() {
        store.addUnlock()
        assertEquals(emptyList<Pair<LocalDate, Int?>>(), store.historyRows())
    }

    @Test
    fun logHasOneTimePerUnlockAndClears() {
        store.addUnlock()
        time = time.plusMinutes(5)
        store.addUnlock()
        val out = ByteArrayOutputStream().also(store::writeLog)
        assertEquals("2026-11-09T10:00+01:00\n2026-11-09T10:05+01:00\n", out.toString())
        assertEquals(2, store.logEntries())
        store.clearLog()
        assertEquals("", ByteArrayOutputStream().also(store::writeLog).toString())
        assertEquals(2, store.todayCount())
    }

    @Test
    fun storedPastDayGoesToHistory() {
        storage.values["day"] = day.minusDays(1).toString()
        storage.values["count"] = "7"
        assertEquals(0, store.todayCount())
        assertEquals(listOf(day.minusDays(1) to 7), store.historyRows())
    }

    @Test
    fun timeZoneMovedBackDoesNotOverwriteHistory() {
        repeat(80) { store.addUnlock() }
        time = OffsetDateTime.of(2026, 11, 10, 0, 30, 0, 0, ZoneOffset.ofHours(1))
        repeat(5) { store.addUnlock() }
        time = time.withOffsetSameInstant(ZoneOffset.ofHours(-5))
        assertEquals(80, store.todayCount())
        repeat(12) { store.addUnlock() }
        time = time.plusHours(10)
        assertEquals(5, store.todayCount())
        assertEquals(listOf(day to 92), store.historyRows())
    }

    @Test
    fun unsetClockKeepsCountingOnStoredDay() {
        repeat(3) { store.addUnlock() }
        time = OffsetDateTime.of(1970, 1, 1, 0, 1, 0, 0, ZoneOffset.UTC)
        store.addUnlock()
        assertEquals(4, store.todayCount())
        assertEquals(emptyList<Pair<LocalDate, Int?>>(), store.historyRows())
    }

    @Test
    fun clockMovedForwardAndBackKeepsBothDays() {
        repeat(4) { store.addUnlock() }
        nextDay(30)
        repeat(2) { store.addUnlock() }
        time = time.minusDays(30)
        assertEquals(4, store.todayCount())
        store.addUnlock()
        assertEquals(5, store.todayCount())
        nextDay(30)
        assertEquals(2, store.todayCount())
    }

    @Test
    fun lastDaysEndsWithTodayOldestFirst() {
        store.addUnlock()
        nextDay(2)
        repeat(3) { store.addUnlock() }
        assertEquals(listOf(day to 1, day.plusDays(1) to null, day.plusDays(2) to 3), store.lastDays(3))
    }

    @Test
    fun dailyAverageSkipsTodayAndMissingDays() {
        repeat(2) { store.addUnlock() }
        nextDay(2)
        repeat(6) { store.addUnlock() }
        nextDay()
        repeat(100) { store.addUnlock() }
        assertEquals(4.0, store.dailyAverage(14)!!, 0.001)
    }

    @Test
    fun dailyAverageIsNullWithoutFinishedDays() {
        store.addUnlock()
        assertEquals(null, store.dailyAverage(14))
    }

    @Test
    fun firstUnlockAfterBootCountsWhenMissed() {
        store.addUnlock()
        val boot = time.plusHours(1)
        time = boot.plusSeconds(30)
        store.addUnlockIfNoneSince(boot)
        assertEquals(2, store.todayCount())
    }

    @Test
    fun firstUnlockAfterBootNotCountedTwice() {
        val boot = time
        time = boot.plusSeconds(10)
        store.addUnlock()
        time = boot.plusSeconds(30)
        store.addUnlockIfNoneSince(boot)
        assertEquals(1, store.todayCount())
    }
}
