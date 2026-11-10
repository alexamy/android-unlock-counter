package com.alexamy.unlockcounter

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

/** One day as the day screen shows it. [times] are the logged unlocks, newest first; [count] is null when there is no data. */
data class Day(
    val date: LocalDate,
    val count: Int?,
    val times: List<OffsetDateTime>,
    val isToday: Boolean,
) {
    /** Unlocks counted but not in the log. */
    val missing: Int get() = ((count ?: 0) - times.size).coerceAtLeast(0)
    val stats: DayStats get() = DayStats.of(times)

    /** Newest first: unlocks [DayStats.BURST] or less apart share one entry, and a gap of [BREAK] or more gets its own. */
    val timeline: List<Entry>
        get() {
            val entries = mutableListOf<Entry>()
            var start = 0
            while (start < times.size) {
                var end = start
                while (end + 1 < times.size && gap(end + 1, end) <= DayStats.BURST) end++
                entries += Entry.Unlocks(times[end], Duration.between(times[end], times[start]), start..end)
                if (end + 1 < times.size && gap(end + 1, end) >= BREAK) entries += Entry.Break(gap(end + 1, end))
                start = end + 1
            }
            return entries
        }

    private fun gap(older: Int, newer: Int): Duration = Duration.between(times[older], times[newer])

    sealed interface Entry {
        /** The unlocks at [indices] of [times], starting at [first] and lasting [span]. */
        data class Unlocks(val first: OffsetDateTime, val span: Duration, val indices: IntRange) : Entry {
            val count: Int get() = indices.count()
        }

        data class Break(val length: Duration) : Entry
    }

    companion object {
        val BREAK: Duration = Duration.ofMinutes(15)
    }
}
