package com.alexamy.unlockcounter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.OffsetDateTime

class DayStatsTest {
    private val start = OffsetDateTime.parse("2026-11-09T08:00:00+01:00")
    private fun at(seconds: Long) = start.plusSeconds(seconds)

    @Test
    fun noUnlocksHasNoValues() =
        assertEquals(DayStats(null, null, null, null, null), DayStats.of(emptyList()))

    @Test
    fun oneUnlockHasNoGaps() =
        assertEquals(DayStats(start, start, null, null, 0), DayStats.of(listOf(start)))

    @Test
    fun twoUnlocksHaveOneGap() {
        val gap = Duration.ofMinutes(7)
        assertEquals(DayStats(start, at(420), gap, gap, 0), DayStats.of(listOf(start, at(420))))
    }

    @Test
    fun firstAndLastFollowTimeNotLogOrder() {
        val stats = DayStats.of(listOf(at(600), start, at(300)))
        assertEquals(start, stats.first)
        assertEquals(at(600), stats.last)
    }

    @Test
    fun longestAndMedianGap() {
        val stats = DayStats.of(listOf(start, at(60), at(360), at(3960)))
        assertEquals(Duration.ofMinutes(60), stats.longestGap)
        assertEquals(Duration.ofMinutes(5), stats.medianGap)
    }

    @Test
    fun medianOfEvenGapsIsTheMiddleMean() =
        assertEquals(Duration.ofSeconds(150), DayStats.of(listOf(start, at(100), at(300), at(1300), at(1400))).medianGap)

    @Test
    fun burstIncludesFiveMinutesExactly() =
        assertEquals(1, DayStats.of(listOf(start, at(300), at(601))).bursts)

    @Test
    fun gapFormat() {
        assertEquals("<1 min", DayStats.formatGap(Duration.ofSeconds(59)))
        assertEquals("6 min", DayStats.formatGap(Duration.ofMinutes(6)))
        assertEquals("3 h 05 min", DayStats.formatGap(Duration.ofMinutes(185)))
    }
}
