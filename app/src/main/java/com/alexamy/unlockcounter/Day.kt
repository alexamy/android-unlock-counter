package com.alexamy.unlockcounter

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * One day as the day screen shows it. [times] are the logged unlocks, newest first; [count] is null when there is no data.
 * [ends] holds, for each of [times], the lock that ended its session, or null when none was logged.
 * [now] is set on today only; [locksSince] is the first day with a logged lock.
 */
data class Day(
    val date: LocalDate,
    val count: Int?,
    val times: List<OffsetDateTime>,
    val isToday: Boolean,
    val burst: Duration = DayStats.DEFAULT_BURST,
    val start: LocalTime = DEFAULT_START,
    val ends: List<OffsetDateTime?> = emptyList(),
    val glance: Duration = DEFAULT_GLANCE,
    val now: OffsetDateTime? = null,
    val locksSince: LocalDate? = null,
) {
    /** Unlocks counted but not in the log. */
    val missing: Int get() = ((count ?: 0) - times.size).coerceAtLeast(0)
    val stats: DayStats get() = DayStats.of(times, burst)

    /** True when at least one session of the day has a logged lock; the day screen then shows sessions. */
    val hasLocks: Boolean get() = ends.any { it != null }

    /**
     * One per unlock, newest first. The newest one today with no lock yet is open and ends now.
     * shortcut: an open session longer than [OPEN_LIMIT] is taken as a missed lock and has no length; a real longer session shows wrong.
     */
    val sessions: List<Session>
        get() = times.mapIndexed { i, start ->
            val end = ends.getOrNull(i)
            val open = end == null && i == 0 && now != null && !now.isBefore(start) && Duration.between(start, now) <= OPEN_LIMIT
            if (open) Session(start, now, open = true) else Session(start, end, open = false)
        }

    /** Each lock and the next unlock, where both are logged, oldest first. */
    val offSpans: List<Pair<OffsetDateTime, OffsetDateTime>>
        get() = sessions.zipWithNext { newer, older -> older.end?.takeUnless { older.open }?.let { it to newer.start } }
            .filterNotNull()
            .reversed()

    val offTimes: List<Duration> get() = offSpans.map { (lock, unlock) -> Duration.between(lock, unlock) }

    /** The oldest of the longest locked spans. */
    val longestOff: Pair<OffsetDateTime, OffsetDateTime>? get() = offSpans.maxByOrNull { (lock, unlock) -> Duration.between(lock, unlock) }

    val screenTime: Duration get() = sessions.mapNotNull { it.length }.fold(Duration.ZERO, Duration::plus)
    val lastLock: OffsetDateTime? get() = sessions.filter { !it.open }.mapNotNull { it.end }.maxByOrNull { it.toInstant() }

    fun isGlance(session: Session): Boolean = !session.open && session.length?.let { it < glance } == true

    /** An unlock [burst] or sooner after the one before it. */
    fun isBurst(index: Int): Boolean = index + 1 < times.size && gap(index + 1, index) <= burst

    /** Finished sessions counted by length: under the glance limit, then up to 10, 30 and more minutes. */
    val lengths: List<Bucket>
        get() {
            val limits = listOf(glance.toMinutes()) + listOf(10L, 30L).filter { it > glance.toMinutes() }
            val bounds = listOf(0L) + limits
            val finished = sessions.filter { !it.open }.mapNotNull { it.length }
            return bounds.mapIndexed { i, from ->
                val to = bounds.getOrNull(i + 1)
                Bucket(from, to, finished.count { it >= Duration.ofMinutes(from) && (to == null || it < Duration.ofMinutes(to)) })
            }
        }

    /**
     * Newest first. Without locks, unlocks [burst] or less apart share one entry, and a gap of [BREAK] or more gets its own.
     * With locks, each session is its own entry, and a break is the time locked between two sessions,
     * or the time between the unlocks where the lock is missing.
     */
    val timeline: List<Entry>
        get() {
            val entries = mutableListOf<Entry>()
            var start = 0
            while (start < times.size) {
                var end = start
                while (!hasLocks && end + 1 < times.size && gap(end + 1, end) <= burst) end++
                entries += Entry.Unlocks(times[end], Duration.between(times[end], times[start]), start..end)
                if (end + 1 < times.size) {
                    val lock = if (hasLocks) ends[end + 1] else null
                    val off = lock?.let { Duration.between(it, times[end]) } ?: gap(end + 1, end)
                    if (off >= BREAK) entries += Entry.Break(off, locked = lock != null)
                }
                start = end + 1
            }
            return entries
        }

    private fun gap(older: Int, newer: Int): Duration = Duration.between(times[older], times[newer])

    data class Session(val start: OffsetDateTime, val end: OffsetDateTime?, val open: Boolean) {
        val length: Duration? get() = end?.let { Duration.between(start, it) }
    }

    /** Sessions from [from] minutes up to, not including, [to] minutes; a null [to] has no upper limit. */
    data class Bucket(val from: Long, val to: Long?, val count: Int)

    sealed interface Entry {
        /** The unlocks at [indices] of [times], starting at [first] and lasting [span]. */
        data class Unlocks(val first: OffsetDateTime, val span: Duration, val indices: IntRange) : Entry {
            val count: Int get() = indices.count()
        }

        /** [locked] when the break runs from a logged lock; otherwise it runs from the unlock before it. */
        data class Break(val length: Duration, val locked: Boolean = false) : Entry
    }

    companion object {
        val BREAK: Duration = Duration.ofMinutes(15)
        val DEFAULT_START: LocalTime = LocalTime.MIDNIGHT
        val DEFAULT_GLANCE: Duration = Duration.ofMinutes(3)
        val OPEN_LIMIT: Duration = Duration.ofHours(2)
    }
}
