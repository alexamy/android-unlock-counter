package com.alexamy.unlockcounter

import java.time.Duration
import java.time.OffsetDateTime

/** Stats of one day's unlock times; a null value has too few unlocks to compute. */
data class DayStats(
    val first: OffsetDateTime?,
    val last: OffsetDateTime?,
    val longestGap: Duration?,
    val medianGap: Duration?,
    val bursts: Int?,
) {
    companion object {
        val BURST: Duration = Duration.ofMinutes(5)

        fun of(times: List<OffsetDateTime>): DayStats {
            val sorted = times.sortedBy { it.toInstant() }
            val gaps = sorted.zipWithNext { a, b -> Duration.between(a, b) }.sorted()
            val median = when {
                gaps.isEmpty() -> null
                gaps.size % 2 == 1 -> gaps[gaps.size / 2]
                else -> (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]).dividedBy(2)
            }
            return DayStats(
                first = sorted.firstOrNull(),
                last = sorted.lastOrNull(),
                longestGap = gaps.lastOrNull(),
                medianGap = median,
                bursts = if (sorted.isEmpty()) null else gaps.count { it <= BURST },
            )
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
