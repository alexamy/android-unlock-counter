package com.alexamy.unlockcounter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayCountTest {
    private val day = LocalDate.of(2026, 11, 9)

    @Test
    fun sameDayKeepsCount() = assertEquals(DayCount(day, 5), DayCount(day, 5).on(day))

    @Test
    fun newDayResetsToZero() = assertEquals(DayCount(day.plusDays(1), 0), DayCount(day, 5).on(day.plusDays(1)))

    @Test
    fun unlockSameDayAddsOne() = assertEquals(DayCount(day, 6), DayCount(day, 5).plusUnlock(day))

    @Test
    fun firstUnlockOfNewDayIsOne() = assertEquals(DayCount(day.plusDays(1), 1), DayCount(day, 5).plusUnlock(day.plusDays(1)))
}
