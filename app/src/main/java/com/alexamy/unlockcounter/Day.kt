package com.alexamy.unlockcounter

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * One day as the day screen shows it; [count] is null when there is no data.
 * [events] are logged unlocks and locks around the day, in any order; the day's unlocks are picked by [start].
 * A session ends at the lock logged right after its unlock, also when that lock falls on the next day.
 * [now] is set on today only; [locksSince] is the first day with a logged lock.
 */
data class Day(
    val date: LocalDate,
    val count: Int?,
    val events: List<LogEvent>,
    val isToday: Boolean,
    val burst: Duration = DayStats.DEFAULT_BURST,
    val start: LocalTime = DEFAULT_START,
    val glance: Duration = DEFAULT_GLANCE,
    val now: OffsetDateTime? = null,
    val locksSince: LocalDate? = null,
    val openLimit: Duration = DEFAULT_OPEN_LIMIT,
) {
    private val paired: List<Pair<OffsetDateTime, OffsetDateTime?>> by lazy {
        val sorted = events.sortedBy { it.time.toInstant() }
        sorted.mapIndexedNotNull { i, event ->
            if (event.isLock || dayOf(event.time, start) != date) null
            else event.time to sorted.getOrNull(i + 1)?.takeIf { it.isLock }?.time
        }.reversed()
    }

    /** The day's logged unlocks, newest first. */
    val times: List<OffsetDateTime> by lazy { paired.map { it.first } }

    /** For each of [times], the lock that ended its session, or null when none was logged. */
    val ends: List<OffsetDateTime?> by lazy { paired.map { it.second } }

    /** Unlocks counted but not in the log. */
    val missing: Int get() = ((count ?: 0) - times.size).coerceAtLeast(0)
    val stats: DayStats by lazy { DayStats.of(times, burst) }

    /** True when at least one session of the day has a logged lock; the day then shows sessions, else unlocks only. */
    private val hasLocks: Boolean by lazy { ends.any { it != null } }

    /** The first day with lock times, when this day comes before it and so shows unlocks only. */
    val locksStart: LocalDate? get() = locksSince?.takeIf { !hasLocks && date < it }

    /**
     * One per unlock, newest first. The newest one today with no lock yet is open and ends now.
     * An open session longer than [openLimit] is taken as a missed lock and has no length.
     */
    val sessions: List<Session> by lazy {
        times.mapIndexed { i, start ->
            val end = ends[i]
            val open = end == null && i == 0 && now != null && !now.isBefore(start) && Duration.between(start, now) <= openLimit
            if (open) Session(start, now, open = true) else Session(start, end, open = false)
        }
    }

    /** Each lock and the next unlock, where both are logged, oldest first. */
    val offSpans: List<Pair<OffsetDateTime, OffsetDateTime>> by lazy {
        sessions.zipWithNext { newer, older -> older.end?.takeUnless { older.open }?.let { it to newer.start } }
            .filterNotNull()
            .reversed()
    }

    val offTimes: List<Duration> get() = offSpans.map { (lock, unlock) -> Duration.between(lock, unlock) }

    /**
     * The longest break, the oldest of equal ones: the longest locked span on a day with locks,
     * else the longest gap between unlocks. The strip outlines it and the timeline marks it.
     */
    val longestBreak: Pair<OffsetDateTime, OffsetDateTime>? by lazy {
        val spans = if (hasLocks) offSpans else times.reversed().zipWithNext()
        spans.maxByOrNull { (from, to) -> Duration.between(from, to) }
    }

    /** What the statistics card lists, by the day's kind: sessions or unlocks only. */
    val statRows: List<Stat>
        get() {
            val longest = longestBreak?.let { (from, to) -> Duration.between(from, to) }
            return if (hasLocks) {
                listOf(
                    Stat.Time(Stat.Name.FIRST_UNLOCK, stats.first),
                    Stat.Time(Stat.Name.LAST_LOCK, lastLock),
                    Stat.Length(Stat.Name.LONGEST_OFF, longest),
                    Stat.Length(Stat.Name.MEDIAN_OFF, DayStats.median(offTimes)),
                    Stat.Length(Stat.Name.SCREEN_TIME, screenTime),
                    Stat.Count(Stat.Name.BURSTS, stats.bursts),
                )
            } else {
                listOf(
                    Stat.Time(Stat.Name.FIRST_UNLOCK, stats.first),
                    Stat.Time(Stat.Name.LAST_UNLOCK, stats.last),
                    Stat.Length(Stat.Name.LONGEST_GAP, longest),
                    Stat.Length(Stat.Name.MEDIAN_GAP, stats.medianGap),
                    Stat.Count(Stat.Name.BURSTS, stats.bursts),
                )
            }
        }

    val screenTime: Duration get() = sessions.mapNotNull { it.length }.fold(Duration.ZERO, Duration::plus)
    val lastLock: OffsetDateTime? get() = sessions.filter { !it.open }.mapNotNull { it.end }.maxByOrNull { it.toInstant() }

    private fun isGlance(session: Session): Boolean = !session.open && session.length?.let { it < glance } == true

    /** Indices of [times] whose session was a glance. */
    val glances: Set<Int> by lazy { sessions.indices.filter { isGlance(sessions[it]) }.toSet() }

    /** An unlock [burst] or sooner after the one before it. */
    private fun isBurst(index: Int): Boolean = index + 1 < times.size && gap(index + 1, index) <= burst

    /** Finished sessions counted by length: under the glance limit, then up to 10, 30 and more minutes; null without locks. */
    val lengths: List<Bucket>?
        get() {
            if (!hasLocks) return null
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
     * or the time between the unlocks where the lock is missing. The [longestBreak] entry is marked.
     */
    val timeline: List<Entry> by lazy {
        val entries = mutableListOf<Entry>()
        var start = 0
        while (start < times.size) {
            var end = start
            while (!hasLocks && end + 1 < times.size && gap(end + 1, end) <= burst) end++
            entries += if (hasLocks) {
                Entry.Use(sessions[start], start, burst = isBurst(start), glance = start in glances)
            } else {
                Entry.Unlocks(times[end], Duration.between(times[end], times[start]), start..end)
            }
            if (end + 1 < times.size) {
                val lock = if (hasLocks) ends[end + 1] else null
                val from = lock ?: times[end + 1]
                val off = Duration.between(from, times[end])
                if (off >= BREAK) entries += Entry.Break(off, locked = lock != null, longest = (from to times[end]) == longestBreak)
            }
            start = end + 1
        }
        entries
    }

    private fun gap(older: Int, newer: Int): Duration = Duration.between(times[older], times[newer])

    data class Session(val start: OffsetDateTime, val end: OffsetDateTime?, val open: Boolean) {
        val length: Duration? get() = end?.let { Duration.between(start, it) }
    }

    /** Sessions from [from] minutes up to, not including, [to] minutes; a null [to] has no upper limit. */
    data class Bucket(val from: Long, val to: Long?, val count: Int)

    sealed interface Entry {
        /** An entry that stands for the unlocks at [indices] of [times]. */
        sealed interface Picked : Entry {
            val indices: IntRange
        }

        /** The unlocks at [indices] of [times], starting at [first] and lasting [span]; shown on a day without locks. */
        data class Unlocks(val first: OffsetDateTime, val span: Duration, override val indices: IntRange) : Picked {
            val count: Int get() = indices.count()
        }

        /** One session, the unlock at [index] of [times]; shown on a day with locks. */
        data class Use(val session: Session, val index: Int, val burst: Boolean, val glance: Boolean) : Picked {
            override val indices: IntRange get() = index..index
        }

        /** [locked] when the break runs from a logged lock; otherwise it runs from the unlock before it. */
        data class Break(val length: Duration, val locked: Boolean = false, val longest: Boolean = false) : Entry
    }

    /** One row of the statistics card; a null value has too little data. */
    sealed interface Stat {
        val name: Name

        data class Time(override val name: Name, val value: OffsetDateTime?) : Stat
        data class Length(override val name: Name, val value: Duration?) : Stat
        data class Count(override val name: Name, val value: Int?) : Stat

        enum class Name { FIRST_UNLOCK, LAST_UNLOCK, LAST_LOCK, LONGEST_GAP, MEDIAN_GAP, LONGEST_OFF, MEDIAN_OFF, SCREEN_TIME, BURSTS }
    }

    companion object {
        val BREAK: Duration = Duration.ofMinutes(15)
        val DEFAULT_START: LocalTime = LocalTime.MIDNIGHT
        val DEFAULT_GLANCE: Duration = Duration.ofMinutes(3)
        val DEFAULT_OPEN_LIMIT: Duration = Duration.ofHours(2)

        /** The day [time] counts on when days begin at [start]. */
        fun dayOf(time: OffsetDateTime, start: LocalTime): LocalDate =
            time.toLocalDateTime().minusSeconds(start.toSecondOfDay().toLong()).toLocalDate()
    }
}
