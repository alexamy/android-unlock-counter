package com.alexamy.unlockcounter

import java.time.Duration
import java.time.OffsetDateTime

/** Stats of one day's unlock times; a null value has too few unlocks to compute. */
data class DayStats(
    val first: OffsetDateTime?,
    val last: OffsetDateTime?,
    val bursts: Int?,
) {
    companion object {
        val DEFAULT_BURST: Duration = Duration.ofMinutes(5)

        /** A burst unlock comes [burst] or sooner after the previous one. */
        fun of(times: List<OffsetDateTime>, burst: Duration = DEFAULT_BURST): DayStats {
            val sorted = times.sortedBy { it.toInstant() }
            val gaps = sorted.zipWithNext { a, b -> Duration.between(a, b) }
            return DayStats(
                first = sorted.firstOrNull(),
                last = sorted.lastOrNull(),
                bursts = if (sorted.isEmpty()) null else gaps.count { it <= burst },
            )
        }

        fun median(durations: List<Duration>): Duration? {
            val sorted = durations.sorted()
            return when {
                sorted.isEmpty() -> null
                sorted.size % 2 == 1 -> sorted[sorted.size / 2]
                else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]).dividedBy(2)
            }
        }

        /** "3 h 05 min", "6 min", or "<1 min". */
        fun formatGap(gap: Duration): String {
            val minutes = gap.toMinutes()
            return when {
                minutes < 1 -> "<1 min"
                minutes < 60 -> "$minutes min"
                else -> "%d h %02d min".format(minutes / 60, minutes % 60)
            }
        }
    }
}
