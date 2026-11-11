package com.alexamy.unlockcounter

import java.time.Duration
import java.time.temporal.ChronoUnit

/** A duration saved as a whole number of [unit]s under [key]; a missing value or one outside [range] reads as [default]. */
enum class DurationSetting(val key: String, val unit: ChronoUnit, val range: LongRange, val default: Duration) {
    /** How close unlocks must be to count as a burst. */
    BURST("burst_minutes", ChronoUnit.MINUTES, 1L..60L, DayStats.DEFAULT_BURST),

    /** Sessions shorter than this count as a glance. */
    GLANCE("glance_minutes", ChronoUnit.MINUTES, 1L..60L, Day.DEFAULT_GLANCE),

    /** How long today's newest session may stay open without a lock before it counts as a missed lock. */
    OPEN_LIMIT("open_limit_hours", ChronoUnit.HOURS, 1L..12L, Day.DEFAULT_OPEN_LIMIT);

    fun of(units: Long): Duration = Duration.of(units, unit)

    fun units(value: Duration): Long = value.seconds / unit.duration.seconds
}
