package com.alexamy.unlockcounter

import java.time.LocalDate

data class DayCount(val day: LocalDate, val count: Int) {
    fun on(today: LocalDate) = if (day == today) this else DayCount(today, 0)

    fun plusUnlock(today: LocalDate) = on(today).let { it.copy(count = it.count + 1) }
}
