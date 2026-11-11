package com.alexamy.unlockcounter

import com.alexamy.unlockcounter.Day.Entry.Break
import com.alexamy.unlockcounter.Day.Entry.Use
import com.alexamy.unlockcounter.Day.Session
import com.alexamy.unlockcounter.Day.Stat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class DayTest {
    private val start = OffsetDateTime.parse("2026-11-09T08:00:00+01:00")
    private fun at(seconds: Long) = start.plusSeconds(seconds)
    private fun timeline(vararg seconds: Long) =
        Day(LocalDate.of(2026, 11, 9), seconds.size, seconds.map { LogEvent(at(it), isLock = false) }, false).timeline

    @Test
    fun emptyDayHasNoEntries() = assertEquals(emptyList<Day.Entry>(), timeline())

    private fun sessions(vararg spans: Pair<Long, Long?>, now: OffsetDateTime? = null): Day {
        val events = spans.flatMap { (unlock, lock) -> listOfNotNull(LogEvent(at(unlock), isLock = false), lock?.let { LogEvent(at(it), isLock = true) }) }
        return Day(LocalDate.of(2026, 11, 9), spans.size, events, now != null, now = now)
    }

    @Test
    fun withLocksEachSessionIsAnEntryAndABreakIsTheLockedTime() = assertEquals(
        listOf(
            Use(Session(at(1020), at(1100), open = false), 0, burst = true, glance = true),
            Use(Session(at(960), at(1000), open = false), 1, burst = false, glance = true),
            Break(Duration.ofMinutes(15), locked = true, longest = true),
            Use(Session(start, at(60), open = false), 2, burst = false, glance = true),
        ),
        sessions(0L to 60L, 960L to 1000L, 1020L to 1100L).timeline,
    )

    @Test
    fun newestSessionTodayWithoutLockIsOpenUntilNow() {
        val day = sessions(0L to 60L, 600L to null, now = at(900))
        assertEquals(Day.Session(at(600), at(900), open = true), day.sessions.first())
        assertEquals(Duration.ofSeconds(360), day.screenTime)
        assertEquals(at(60), day.lastLock)
    }

    @Test
    fun unlockWithoutLockHasNoLengthAndIsSkipped() {
        val day = sessions(0L to null, 600L to 700L)
        assertEquals(null, day.sessions.last().length)
        assertEquals(Duration.ofSeconds(100), day.screenTime)
        assertEquals(emptyList<Duration>(), day.offTimes)
    }

    @Test
    fun lengthsSplitAtTheGlanceLimit() {
        val day = sessions(0L to 179L, 1000L to 1180L, 2000L to 2700L, 5000L to 7000L)
        assertEquals(listOf(1, 1, 1, 1), day.lengths?.map { it.count })
        assertEquals(listOf(0L, 3L, 10L, 30L), day.lengths?.map { it.from })
        assertEquals(setOf(3), day.glances)
    }

    @Test
    fun offTimesRunFromLockToNextUnlockOldestFirst() = assertEquals(
        listOf(Duration.ofSeconds(540), Duration.ofSeconds(100)),
        sessions(0L to 60L, 600L to 900L, 1000L to 1100L).offTimes,
    )

    @Test
    fun breakAfterAnUnlockWithoutLockRunsFromTheUnlock() = assertEquals(
        listOf(
            Use(Session(at(3600), at(3700), open = false), 0, burst = false, glance = true),
            Break(Duration.ofHours(1)),
            Use(Session(start, null, open = false), 1, burst = false, glance = false),
        ),
        sessions(0L to null, 3600L to 3700L).timeline,
    )

    @Test
    fun openSessionLongerThanTheLimitIsAMissedLock() {
        val day = sessions(0L to null, now = at(Day.DEFAULT_OPEN_LIMIT.seconds + 1))
        assertEquals(Day.Session(start, null, open = false), day.sessions.single())
        assertEquals(Duration.ZERO, day.screenTime)
    }

    @Test
    fun longestBreakIsTheOldestLongestLockedSpan() = assertEquals(
        at(60) to at(600),
        sessions(0L to 60L, 600L to 700L, 1240L to 1300L).longestBreak,
    )

    @Test
    fun statRowsWithLocksShowSessions() = assertEquals(
        listOf(
            Stat.Time(Stat.Name.FIRST_UNLOCK, start),
            Stat.Time(Stat.Name.LAST_LOCK, at(700)),
            Stat.Length(Stat.Name.LONGEST_OFF, Duration.ofSeconds(540)),
            Stat.Length(Stat.Name.MEDIAN_OFF, Duration.ofSeconds(540)),
            Stat.Length(Stat.Name.SCREEN_TIME, Duration.ofSeconds(160)),
            Stat.Count(Stat.Name.BURSTS, 0),
        ),
        sessions(0L to 60L, 600L to 700L).statRows,
    )

    @Test
    fun pairsTheLockRightAfterAnUnlockAlsoOnTheNextDay() {
        val night = OffsetDateTime.parse("2026-11-09T23:59:00+01:00")
        val events = listOf(
            LogEvent(night.plusMinutes(5), isLock = true),
            LogEvent(night.plusMinutes(10), isLock = false),
            LogEvent(night, isLock = false),
            LogEvent(start, isLock = false),
        )
        val day = Day(LocalDate.of(2026, 11, 9), 2, events, false)
        assertEquals(listOf(night, start), day.times)
        assertEquals(listOf(night.plusMinutes(5), null), day.ends)
    }

    @Test
    fun aPastDayBeforeLockTrackingIsLegacy() {
        val day = sessions(0L to null)
        assertEquals(true, day.legacy)
        assertEquals(true, day.copy(locksSince = LocalDate.of(2026, 11, 10)).legacy)
        assertEquals(false, day.copy(locksSince = LocalDate.of(2026, 11, 9)).legacy)
    }

    @Test
    fun todayIsNeverLegacy() = assertEquals(false, sessions(0L to null, now = at(60)).legacy)
}
