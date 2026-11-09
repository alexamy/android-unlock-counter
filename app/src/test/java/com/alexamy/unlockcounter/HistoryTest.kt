package com.alexamy.unlockcounter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun noRecordsGivesNoRows() = assertEquals(emptyList<Pair<LocalDate, Int?>>(), historyRows(emptyMap(), today))

    @Test
    fun rowsAreNewestFirstWithGapsAsMissing() = assertEquals(
        listOf(today.minusDays(1) to 7, today.minusDays(2) to null, today.minusDays(3) to 4),
        historyRows(mapOf(today.minusDays(1) to 7, today.minusDays(3) to 4), today),
    )

    @Test
    fun todayIsNotARow() = assertEquals(emptyList<Pair<LocalDate, Int?>>(), historyRows(mapOf(today to 3), today))
}
