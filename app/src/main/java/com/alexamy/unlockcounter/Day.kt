package com.alexamy.unlockcounter

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * One day as the day screen shows it. [times] are the logged unlocks, newest first;
 * [count] is the day's unlock count, null when there is no data; [hour] is the current hour on today, else null.
 */
data class Day(
    val date: LocalDate,
    val count: Int?,
    val times: List<OffsetDateTime>,
    val hour: Int?,
) {
    val isToday: Boolean get() = hour != null
    val perHour: List<Int> get() = (0..23).map { h -> times.count { it.hour == h } }

    /** Unlocks counted but not in the log. */
    val missing: Int get() = ((count ?: 0) - times.size).coerceAtLeast(0)
    val stats: DayStats get() = DayStats.of(times)
}
