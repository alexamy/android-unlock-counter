package com.alexamy.unlockcounter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
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
        override fun <T> readLog(block: (Sequence<String>) -> T): T = log.toString().reader().buffered().useLines(block)
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
        assertEquals("2026-11-09T10:00+01:00 unlock\n2026-11-09T10:05+01:00 unlock\n", out.toString())
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
    fun weeksSkipEmptyWeeksAndEndWithCurrent() {
        repeat(2) { store.addUnlock() }
        nextDay(15)
        repeat(3) { store.addUnlock() }
        val weeks = store.weeks()
        assertEquals(listOf(day, day.plusDays(14)), weeks.map { it.monday })
        assertEquals(listOf(day to 2) + (1L..6L).map { day.plusDays(it) to null }, weeks.first().days)
        assertEquals(listOf(day.plusDays(14) to null, day.plusDays(15) to 3), weeks.last().days)
        assertEquals(2.0, weeks.first().average!!, 0.001)
        assertEquals(null, weeks.last().average)
    }

    @Test
    fun weekAverageSkipsTodayAndMissingDays() {
        repeat(2) { store.addUnlock() }
        nextDay(2)
        repeat(6) { store.addUnlock() }
        nextDay()
        repeat(100) { store.addUnlock() }
        assertEquals(4.0, store.weeks().single().average!!, 0.001)
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

    @Test
    fun noUnlockAddedWithoutAnyRecord() {
        store.addUnlockIfNoneSince(time.minusMinutes(1))
        assertEquals(0, store.todayCount())
    }

    @Test
    fun dayTimesUseRecordedOffsetNewestFirst() {
        storage.log.append("2026-11-09T23:30+01:00\n2026-11-10T00:10+01:00\n2026-11-09T22:00-05:00\n")
        assertEquals(
            listOf(OffsetDateTime.parse("2026-11-09T22:00-05:00"), OffsetDateTime.parse("2026-11-09T23:30+01:00")),
            store.day(day).times,
        )
    }

    @Test
    fun dayTimesSkipUnreadableLines() {
        storage.log.append("2026-11-09T08:00+01:00\ngarbage\n2026-11-09T0")
        assertEquals(listOf(OffsetDateTime.parse("2026-11-09T08:00+01:00")), store.day(day).times)
    }

    @Test
    fun dayWithoutLogHasNoTimes() {
        val today = store.day(day)
        assertEquals(emptyList<OffsetDateTime>(), today.times)
        assertEquals(0, today.missing)
    }

    @Test
    fun dayCountIsTodayHistoryOrNull() {
        repeat(4) { store.addUnlock() }
        nextDay(2)
        store.addUnlock()
        assertEquals(1, store.day(day.plusDays(2)).count)
        assertEquals(true, store.day(day.plusDays(2)).isToday)
        assertEquals(4, store.day(day).count)
        assertEquals(false, store.day(day).isToday)
        assertEquals(null, store.day(day.plusDays(1)).count)
    }

    @Test
    fun dayMissing() {
        store.addUnlock()
        store.clearLog()
        time = time.plusMinutes(5)
        store.addUnlock()
        time = time.plusHours(1)
        store.addUnlock()
        val today = store.day(day)
        assertEquals(1, today.missing)
        assertEquals(true, today.isToday)
    }

    @Test
    fun todayBeforeClockSyncIsStoredDay() {
        store.addUnlock()
        time = OffsetDateTime.of(1970, 1, 1, 0, 1, 0, 0, ZoneOffset.UTC)
        assertEquals(true, store.day(day).isToday)
        assertEquals(1, store.day(day).count)
    }

    @Test
    fun burstDefaultsToFiveMinutesAndIsSaved() {
        assertEquals(Duration.ofMinutes(5), store.burst)
        store.burst = Duration.ofMinutes(2)
        assertEquals(Duration.ofMinutes(2), CounterStore(storage) { time }.burst)
        assertEquals(Duration.ofMinutes(2), store.day(day).burst)
    }

    @Test
    fun unlockBeforeDayStartCountsOnPreviousDay() {
        store.dayStart = LocalTime.of(1, 0)
        store.addUnlock()
        time = OffsetDateTime.of(2026, 11, 10, 0, 30, 0, 0, ZoneOffset.ofHours(1))
        store.addUnlock()
        assertEquals(2, store.todayCount())
        time = time.plusHours(1)
        assertEquals(0, store.todayCount())
        assertEquals(listOf(day to 2), store.historyRows())
    }

    @Test
    fun dayGroupsLogByDayStart() {
        store.dayStart = LocalTime.of(1, 0)
        storage.log.append("2026-11-09T00:59+01:00\n2026-11-09T23:00+01:00\n2026-11-10T00:30+01:00\n2026-11-10T01:00+01:00\n")
        assertEquals(
            listOf(OffsetDateTime.parse("2026-11-10T00:30+01:00"), OffsetDateTime.parse("2026-11-09T23:00+01:00")),
            store.day(day).times,
        )
    }

    @Test
    fun changingDayStartKeepsCounts() {
        repeat(3) { store.addUnlock() }
        store.dayStart = LocalTime.NOON
        assertEquals(3, store.todayCount())
        store.dayStart = LocalTime.MIDNIGHT
        assertEquals(3, store.todayCount())
    }

    @Test
    fun laterDayStartKeepsTodayUntilItBegins() {
        repeat(2) { store.addUnlock() }
        nextDay()
        repeat(3) { store.addUnlock() }
        store.dayStart = LocalTime.NOON
        assertEquals(3, store.todayCount())
        assertEquals(listOf(day to 2), store.historyRows())
        time = time.plusDays(1).withHour(11)
        assertEquals(3, store.todayCount())
        time = time.withHour(13)
        assertEquals(0, store.todayCount())
        assertEquals(listOf(day.plusDays(1) to 3, day to 2), store.historyRows())
    }

    @Test
    fun savedBurstOutOfRangeFallsBackToDefault() {
        storage.values["burst_minutes"] = "0"
        assertEquals(Duration.ofMinutes(5), store.burst)
    }

    @Test
    fun dayStartDefaultsToMidnightAndIsSaved() {
        assertEquals(LocalTime.MIDNIGHT, store.dayStart)
        store.dayStart = LocalTime.of(4, 30)
        assertEquals(LocalTime.of(4, 30), CounterStore(storage) { time }.dayStart)
        assertEquals(LocalTime.of(4, 30), store.day(day).start)
    }

    @Test
    fun lockIsLoggedOncePerUnlockAndNotCounted() {
        store.addLock()
        store.addUnlock()
        time = time.plusMinutes(2)
        store.addLock()
        time = time.plusMinutes(1)
        store.addLock()
        assertEquals("2026-11-09T10:00+01:00 unlock\n2026-11-09T10:02+01:00 lock\n", storage.log.toString())
        assertEquals(1, store.logEntries())
        assertEquals(1, store.todayCount())
    }

    @Test
    fun dayPairsEachUnlockWithTheLockRightAfterIt() {
        storage.log.append(
            "2026-11-09T08:00+01:00\n" +
                "2026-11-09T09:00+01:00 unlock\n2026-11-09T09:02+01:00 lock\n" +
                "2026-11-09T23:59+01:00 unlock\n2026-11-10T00:04+01:00 lock\n",
        )
        nextDay()
        val past = store.day(day)
        assertEquals(listOf("23:59", "09:00", "08:00"), past.times.map { it.toLocalTime().toString() })
        assertEquals(listOf("00:04", "09:02", null), past.ends.map { it?.toLocalTime()?.toString() })
    }

    @Test
    fun locksSinceIsTheDayOfTheFirstLock() {
        store.addUnlock()
        store.addLock()
        nextDay()
        store.addUnlock()
        store.addLock()
        assertEquals(day, store.day(day).locksSince)
    }

    @Test
    fun glanceDefaultsToThreeMinutesAndIsSaved() {
        assertEquals(Duration.ofMinutes(3), store.glance)
        store.glance = Duration.ofMinutes(7)
        assertEquals(Duration.ofMinutes(7), store.glance)
        storage.values["glance_minutes"] = "0"
        assertEquals(Duration.ofMinutes(3), store.glance)
    }

    @Test
    fun clearLogForgetsWhenLocksStarted() {
        store.addUnlock()
        store.addLock()
        store.clearLog()
        assertEquals(null, store.day(day).locksSince)
    }

    @Test
    fun lockBeforeClockSyncDoesNotMarkWhenLocksStarted() {
        store.addUnlock()
        time = OffsetDateTime.of(1970, 1, 1, 0, 5, 0, 0, ZoneOffset.UTC)
        store.addLock()
        assertEquals(null, storage.values["locks_since"])
    }

    @Test
    fun openLimitDefaultsToTwoHoursAndIsSaved() {
        assertEquals(Duration.ofHours(2), store.openLimit)
        store.openLimit = Duration.ofHours(5)
        assertEquals(Duration.ofHours(5), store.day(day).openLimit)
        storage.values["open_limit_hours"] = "13"
        assertEquals(Duration.ofHours(2), store.openLimit)
    }
}
