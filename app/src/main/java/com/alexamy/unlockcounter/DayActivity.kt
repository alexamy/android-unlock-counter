package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ln

class DayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val day = CounterStore(this).day(LocalDate.parse(intent.getStringExtra(EXTRA_DAY)))
        val times = day.times
        val stats = day.stats
        val sessions = day.sessions

        findViewById<TextView>(R.id.title).text =
            if (day.isToday) getString(R.string.today) else day.date.format(HistoryTab.DATE)

        findViewById<View>(R.id.day_empty).visibility = if (times.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.day_content).visibility = if (times.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.partial).apply {
            visibility = if (day.missing > 0) View.VISIBLE else View.GONE
            text = getString(R.string.times_partial, times.size, day.count)
        }
        val locksLater = !day.hasLocks && day.locksSince?.let { day.date < it } == true
        findViewById<TextView>(R.id.locks_note).apply {
            visibility = if (locksLater) View.VISIBLE else View.GONE
            text = getString(R.string.locks_since, day.locksSince?.format(HistoryTab.DATE))
        }

        val first = stats.first?.format(MINUTE) ?: HistoryTab.NO_DATA
        val last = stats.last?.format(MINUTE) ?: HistoryTab.NO_DATA
        val longest = stats.longestGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        val bursts = stats.bursts?.toString() ?: HistoryTab.NO_DATA
        val offTimes = day.offTimes
        val longestOff = day.longestOff?.let { (lock, unlock) -> Duration.between(lock, unlock) }
        fun format(d: Duration?) = d?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA

        fold(R.id.lengths_head, R.id.lengths_chevron, R.id.lengths_body, expanded = true)
        fold(R.id.stats_head, R.id.stats_chevron, R.id.stats_body, expanded = false)
        findViewById<View>(R.id.lengths_card).visibility = if (day.hasLocks) View.VISIBLE else View.GONE
        if (day.hasLocks) showLengths(day.lengths)
        showStats(
            if (day.hasLocks) {
                listOf(
                    R.string.stat_first_unlock to first,
                    R.string.stat_last_lock to (day.lastLock?.format(MINUTE) ?: HistoryTab.NO_DATA),
                    R.string.stat_longest_off to format(longestOff),
                    R.string.stat_median_off to format(DayStats.median(offTimes)),
                    R.string.stat_screen_time to format(day.screenTime),
                    R.string.stat_bursts to bursts,
                )
            } else {
                listOf(
                    R.string.stat_first_unlock to first,
                    R.string.stat_last_unlock to last,
                    R.string.stat_longest_gap to longest,
                    R.string.stat_median_gap to format(stats.medianGap),
                    R.string.stat_bursts to bursts,
                )
            },
        )

        val timeline = day.timeline
        val strip = findViewById<DayStrip>(R.id.strip)
        val list = findViewById<ListView>(R.id.timeline)
        strip.show(times, day.start, sessions.indices.filter { day.isGlance(sessions[it]) }.toSet(), if (day.hasLocks) day.longestOff else null)
        strip.contentDescription = getString(R.string.strip_description, first, last, if (day.hasLocks) format(longestOff) else longest)
        val longestBreak = if (day.hasLocks) longestOff else stats.longestGap
        // The oldest of equal longest breaks, as the strip outlines the first one in the day.
        list.adapter = TimelineAdapter(day, timeline, timeline.indexOfLast { it is Day.Entry.Break && it.locked == day.hasLocks && it.length == longestBreak })
        list.setOnItemClickListener { _, _, position, _ -> strip.lit = (timeline[position] as Day.Entry.Unlocks).indices }
        strip.onTap = { index ->
            val position = timeline.indexOfFirst { it is Day.Entry.Unlocks && index in it.indices }
            list.setItemChecked(position, true)
            list.post { list.smoothScrollToPositionFromTop(position, 0) }
            strip.lit = (timeline[position] as Day.Entry.Unlocks).indices
        }
    }

    private fun fold(head: Int, chevron: Int, body: Int, expanded: Boolean) {
        val headView = findViewById<View>(head)
        val chevronView = findViewById<View>(chevron)
        val bodyView = findViewById<View>(body)
        fun apply(open: Boolean) {
            bodyView.visibility = if (open) View.VISIBLE else View.GONE
            chevronView.rotation = if (open) -90f else 90f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                headView.stateDescription = getString(if (open) R.string.fold_expanded else R.string.fold_collapsed)
            }
        }
        apply(expanded)
        headView.setOnClickListener { apply(bodyView.visibility != View.VISIBLE) }
    }

    private fun showStats(rows: List<Pair<Int, String>>) {
        val body = findViewById<LinearLayout>(R.id.stats_body)
        rows.forEach { (label, value) ->
            body.addView(layoutInflater.inflate(R.layout.row_stat, body, false).apply {
                findViewById<TextView>(R.id.label).setText(label)
                findViewById<TextView>(R.id.value).text = value
            })
        }
    }

    private fun showLengths(buckets: List<Day.Bucket>) {
        val body = findViewById<LinearLayout>(R.id.lengths_body)
        val max = buckets.maxOf { it.count }.coerceAtLeast(1)
        buckets.forEachIndexed { i, bucket ->
            body.addView(layoutInflater.inflate(R.layout.row_bucket, body, false).apply {
                findViewById<TextView>(R.id.label).text = when {
                    i == 0 -> getString(R.string.bucket_under, bucket.to)
                    bucket.to == null -> getString(R.string.bucket_over, bucket.from)
                    else -> getString(R.string.bucket_range, bucket.from, bucket.to)
                }
                findViewById<TextView>(R.id.count).text = bucket.count.toString()
                weigh(findViewById(R.id.bar), findViewById(R.id.rest), bucket.count.toFloat() / max)
                findViewById<View>(R.id.bar).setBackgroundColor(getColor(if (i == 0) R.color.burst else R.color.accent))
            })
        }
    }

    /** Splits a bar track: [bar] takes [share] of it, at least a sliver so an empty row still shows its track start. */
    private fun weigh(bar: View, rest: View, share: Float) {
        val s = share.coerceIn(0.01f, 1f)
        bar.layoutParams = (bar.layoutParams as LinearLayout.LayoutParams).apply { weight = s }
        rest.layoutParams = (rest.layoutParams as LinearLayout.LayoutParams).apply { weight = 1 - s }
    }

    /** An accent bar cut into dashes, for a session that is still open. */
    private fun dashes(): Drawable = object : Drawable() {
        private val density = resources.displayMetrics.density
        private val paint = Paint().apply {
            color = getColor(R.color.accent)
            pathEffect = DashPathEffect(floatArrayOf(5 * density, 3 * density), 0f)
        }

        override fun draw(canvas: Canvas) {
            paint.strokeWidth = bounds.height().toFloat()
            canvas.drawLine(bounds.left.toFloat(), bounds.exactCenterY(), bounds.right.toFloat(), bounds.exactCenterY(), paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private inner class TimelineAdapter(val day: Day, val entries: List<Day.Entry>, val longest: Int) : BaseAdapter() {
        private val sessions = day.sessions
        private val longestSession = sessions.mapNotNull { it.length }.maxOrNull()?.takeIf { !it.isZero } ?: Duration.ofMinutes(1)

        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(position: Int) = when {
            entries[position] is Day.Entry.Break -> 1
            day.hasLocks -> 2
            else -> 0
        }
        override fun isEnabled(position: Int) = entries[position] is Day.Entry.Unlocks

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val entry = entries[position]) {
                is Day.Entry.Unlocks -> if (day.hasLocks) session(entry.indices.first, convertView, parent) else (convertView ?: layoutInflater.inflate(R.layout.row_unlock, parent, false)).apply {
                    findViewById<TextView>(R.id.time).text = entry.first.format(MINUTE)
                    findViewById<TextView>(R.id.note).text =
                        if (entry.count > 1) getString(R.string.timeline_burst, entry.count, DayStats.formatGap(entry.span)) else ""
                }
                is Day.Entry.Break -> (convertView ?: layoutInflater.inflate(R.layout.row_break, parent, false)).apply {
                    val isLongest = position == longest
                    val color = getColor(if (isLongest) R.color.accent else R.color.bar_past)
                    // Height grows with the log of the break, so a 6 h night does not fill the screen.
                    minimumHeight = (resources.displayMetrics.density * (20 + 22 * ln(1.0 + entry.length.toMinutes() / 15.0) / ln(2.0))).toInt()
                    findViewById<View>(R.id.rail).setBackgroundColor(color)
                    val label = when {
                        entry.locked && isLongest -> R.string.timeline_locked_longest
                        entry.locked -> R.string.timeline_locked
                        isLongest -> R.string.timeline_break_longest
                        else -> R.string.timeline_break
                    }
                    findViewById<TextView>(R.id.label).apply {
                        text = getString(label, DayStats.formatGap(entry.length))
                        setTextColor(if (isLongest) color else getColor(R.color.text_muted))
                    }
                }
            }

        private fun session(index: Int, convertView: View?, parent: ViewGroup): View =
            (convertView ?: layoutInflater.inflate(R.layout.row_session, parent, false)).apply {
                val session = sessions[index]
                val start = session.start.format(MINUTE)
                val end = when {
                    session.open -> getString(R.string.session_now)
                    else -> session.end?.format(MINUTE)
                }
                val text = SpannableString(if (end == null) start else getString(R.string.session_range, start, end))
                if (day.isBurst(index)) text.setSpan(ForegroundColorSpan(getColor(R.color.burst)), 0, start.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (session.open) text.setSpan(ForegroundColorSpan(getColor(R.color.accent)), text.length - end!!.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                findViewById<TextView>(R.id.time).text = text

                val glance = day.isGlance(session)
                val length = session.length
                findViewById<TextView>(R.id.length).apply {
                    this.text = length?.let(DayStats::formatGap) ?: ""
                    setTextColor(getColor(if (glance) R.color.burst else R.color.text_muted))
                }
                val bar = findViewById<View>(R.id.bar)
                val rest = findViewById<View>(R.id.rest)
                (bar.parent as View).visibility = if (length == null) View.GONE else View.VISIBLE
                if (length != null) {
                    weigh(bar, rest, length.toMillis().toFloat() / longestSession.toMillis())
                    if (session.open) bar.background = dashes()
                    else bar.setBackgroundColor(getColor(if (glance) R.color.burst else R.color.accent))
                }
            }
    }

    companion object {
        private const val EXTRA_DAY = "day"
        private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun intent(context: Context, day: LocalDate): Intent =
            Intent(context, DayActivity::class.java).putExtra(EXTRA_DAY, day.toString())
    }
}
