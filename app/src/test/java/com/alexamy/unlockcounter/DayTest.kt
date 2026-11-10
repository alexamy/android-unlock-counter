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
}
