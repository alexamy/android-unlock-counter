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

    /** A past day recorded before lock tracking started; it has no sessions to show. */
    val legacy: Boolean get() = !isToday && locksSince?.let { date < it } != false

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

    /** The longest locked span of [BREAK] or more, the oldest of equal ones. The strip outlines it and the timeline marks it. */
    val longestBreak: Pair<OffsetDateTime, OffsetDateTime>? by lazy {
        offSpans.filter { (from, to) -> Duration.between(from, to) >= BREAK }.maxByOrNull { (from, to) -> Duration.between(from, to) }
    }

    /** What the statistics card lists. */
    val statRows: List<Stat>
        get() = listOf(
            Stat.Time(Stat.Name.FIRST_UNLOCK, stats.first),
            Stat.Time(Stat.Name.LAST_LOCK, lastLock),
            Stat.Length(Stat.Name.LONGEST_OFF, offTimes.maxOrNull()),
            Stat.Length(Stat.Name.MEDIAN_OFF, DayStats.median(offTimes)),
            Stat.Length(Stat.Name.SCREEN_TIME, screenTime),
            Stat.Count(Stat.Name.BURSTS, stats.bursts),
        )

    val screenTime: Duration get() = sessions.mapNotNull { it.length }.fold(Duration.ZERO, Duration::plus)
    val lastLock: OffsetDateTime? get() = sessions.filter { !it.open }.mapNotNull { it.end }.maxByOrNull { it.toInstant() }

    private fun isGlance(session: Session): Boolean = !session.open && session.length?.let { it < glance } == true

    /** Indices of [times] whose session was a glance. */
    val glances: Set<Int> by lazy { sessions.indices.filter { isGlance(sessions[it]) }.toSet() }

    /** An unlock [burst] or sooner after the one before it. */
    private fun isBurst(index: Int): Boolean = index + 1 < times.size && gap(index + 1, index) <= burst

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
     * Newest first: each session is an entry, and a break of [BREAK] or more between two sessions gets its own.
     * A break runs from the lock, or from the unlock where the lock is missing. The [longestBreak] entry is marked.
     */
    val timeline: List<Entry> by lazy {
        times.indices.flatMap { i ->
            val use = Entry.Use(sessions[i], i, burst = isBurst(i), glance = i in glances)
            if (i + 1 == times.size) return@flatMap listOf(use)
            val lock = ends[i + 1]
            val from = lock ?: times[i + 1]
            val off = Duration.between(from, times[i])
            if (off < BREAK) listOf(use)
            else listOf(use, Entry.Break(off, locked = lock != null, longest = (from to times[i]) == longestBreak))
        }
    }

    private fun gap(older: Int, newer: Int): Duration = Duration.between(times[older], times[newer])

    data class Session(val start: OffsetDateTime, val end: OffsetDateTime?, val open: Boolean) {
        val length: Duration? get() = end?.let { Duration.between(start, it) }
    }

    /** Sessions from [from] minutes up to, not including, [to] minutes; a null [to] has no upper limit. */
    data class Bucket(val from: Long, val to: Long?, val count: Int)

    sealed interface Entry {
        /** One session, the unlock at [index] of [times]. */
        data class Use(val session: Session, val index: Int, val burst: Boolean, val glance: Boolean) : Entry

        /** [locked] when the break runs from a logged lock; otherwise it runs from the unlock before it. */
        data class Break(val length: Duration, val locked: Boolean = false, val longest: Boolean = false) : Entry
    }

    /** One row of the statistics card; a null value has too little data. */
    sealed interface Stat {
        val name: Name

        data class Time(override val name: Name, val value: OffsetDateTime?) : Stat
        data class Length(override val name: Name, val value: Duration?) : Stat
        data class Count(override val name: Name, val value: Int?) : Stat

        enum class Name { FIRST_UNLOCK, LAST_LOCK, LONGEST_OFF, MEDIAN_OFF, SCREEN_TIME, BURSTS }
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
