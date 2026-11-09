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
        override fun set(key: String, value: String) { values[key] = value }
        override fun keys() = values.keys
        override fun appendLog(text: String) { log.append(text) }
        override fun writeLog(out: OutputStream) = out.write(log.toString().toByteArray())
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
    fun readsOldIntData() {
        storage.values["day"] = day.minusDays(1).toString()
        storage.values["count"] = "7"
        assertEquals(0, store.todayCount())
        assertEquals(listOf(day.minusDays(1) to 7), store.historyRows())
    }
}
