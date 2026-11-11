package com.alexamy.unlockcounter

import com.alexamy.unlockcounter.Day.Entry.Break
import com.alexamy.unlockcounter.Day.Entry.Unlocks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class DayTest {
    private val start = OffsetDateTime.parse("2026-11-09T08:00:00+01:00")
    private fun at(seconds: Long) = start.plusSeconds(seconds)
    private fun timeline(vararg seconds: Long) =
        Day(LocalDate.of(2026, 11, 9), seconds.size, seconds.sortedDescending().map(::at), false).timeline

    @Test
    fun emptyDayHasNoEntries() = assertEquals(emptyList<Day.Entry>(), timeline())

    @Test
    fun unlocksFiveMinutesApartFoldNewestFirst() = assertEquals(
        listOf(Unlocks(at(601), Duration.ZERO, 0..0), Unlocks(start, Duration.ofSeconds(300), 1..2)),
        timeline(0, 300, 601),
    )

    @Test
    fun gapOfFifteenMinutesIsABreak() = assertEquals(
        listOf(Unlocks(at(900), Duration.ZERO, 0..0), Break(Duration.ofMinutes(15)), Unlocks(start, Duration.ZERO, 1..1)),
        timeline(0, 900),
    )

    @Test
    fun gapUnderFifteenMinutesIsNoBreak() = assertEquals(
        listOf(Unlocks(at(899), Duration.ZERO, 0..0), Unlocks(start, Duration.ZERO, 1..1)),
        timeline(0, 899),
    )

    private fun sessions(vararg spans: Pair<Long, Long?>, now: OffsetDateTime? = null): Day {
        val sorted = spans.sortedByDescending { it.first }
        return Day(
            LocalDate.of(2026, 11, 9), spans.size, sorted.map { at(it.first) }, now != null,
            ends = sorted.map { it.second?.let(::at) }, now = now,
        )
    }

    @Test
    fun withLocksEachSessionIsAnEntryAndABreakIsTheLockedTime() = assertEquals(
        listOf(Unlocks(at(1020), Duration.ZERO, 0..0), Unlocks(at(960), Duration.ZERO, 1..1), Break(Duration.ofMinutes(15), locked = true), Unlocks(start, Duration.ZERO, 2..2)),
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
        assertEquals(listOf(1, 1, 1, 1), day.lengths.map { it.count })
        assertEquals(listOf(0L, 3L, 10L, 30L), day.lengths.map { it.from })
        assertEquals(listOf(true, false, false, false), day.sessions.reversed().map(day::isGlance))
    }

    @Test
    fun offTimesRunFromLockToNextUnlockOldestFirst() = assertEquals(
        listOf(Duration.ofSeconds(540), Duration.ofSeconds(100)),
        sessions(0L to 60L, 600L to 900L, 1000L to 1100L).offTimes,
    )

    @Test
    fun breakAfterAnUnlockWithoutLockRunsFromTheUnlock() = assertEquals(
        listOf(Unlocks(at(3600), Duration.ZERO, 0..0), Break(Duration.ofHours(1)), Unlocks(start, Duration.ZERO, 1..1)),
        sessions(0L to null, 3600L to 3700L).timeline,
    )

    @Test
    fun openSessionLongerThanTheLimitIsAMissedLock() {
        val day = sessions(0L to null, now = at(Day.OPEN_LIMIT.seconds + 1))
        assertEquals(Day.Session(start, null, open = false), day.sessions.single())
        assertEquals(Duration.ZERO, day.screenTime)
    }

    @Test
    fun longestOffIsTheOldestLongestLockedSpan() = assertEquals(
        at(60) to at(600),
        sessions(0L to 60L, 600L to 700L, 1240L to 1300L).longestOff,
    )
}
