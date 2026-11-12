package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Context
import android.content.Intent
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
        fold(R.id.lengths_head, R.id.lengths_chevron, R.id.lengths_body, expanded = true)
        fold(R.id.stats_head, R.id.stats_chevron, R.id.stats_body, expanded = false)
    }

    // Built on each resume, so a day seen again after a lock or an app switch shows the new unlocks.
    override fun onResume() {
        super.onResume()
        val day = CounterStore(this).day(LocalDate.parse(intent.getStringExtra(EXTRA_DAY)))
        val times = day.times

        findViewById<TextView>(R.id.title).text =
            if (day.isToday) getString(R.string.today) else day.date.format(HistoryTab.DATE)

        val empty = times.isEmpty() || day.legacy
        findViewById<TextView>(R.id.day_empty).apply {
            visibility = if (empty) View.VISIBLE else View.GONE
            setText(if (day.legacy) R.string.day_legacy else R.string.day_no_times)
        }
        findViewById<View>(R.id.day_content).visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) return
        findViewById<TextView>(R.id.partial).apply {
            visibility = if (day.missing > 0) View.VISIBLE else View.GONE
            text = getString(R.string.times_partial, times.size, day.count)
        }

        showLengths(day.lengths)
        showStats(day.statRows)

        val timeline = day.timeline
        val strip = findViewById<DayStrip>(R.id.strip)
        val list = findViewById<ListView>(R.id.timeline)
        strip.show(times, day.start, day.glances, day.longestBreak)
        val stats = day.stats
        val longest = day.longestBreak?.let { (from, to) -> Duration.between(from, to) }
        strip.contentDescription = getString(
            R.string.strip_description,
            stats.first?.format(MINUTE) ?: HistoryTab.NO_DATA,
            stats.last?.format(MINUTE) ?: HistoryTab.NO_DATA,
            longest?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA,
        )
        list.adapter = TimelineAdapter(timeline)
        list.setOnItemClickListener { _, _, position, _ -> strip.lit = (timeline[position] as Day.Entry.Use).index.let { it..it } }
        strip.onTap = { index ->
            val position = timeline.indexOfFirst { it is Day.Entry.Use && it.index == index }
            list.setItemChecked(position, true)
            list.post { list.smoothScrollToPositionFromTop(position, 0) }
            strip.lit = index..index
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

    private fun showStats(rows: List<Day.Stat>) {
        val body = findViewById<LinearLayout>(R.id.stats_body)
        body.removeAllViews()
        rows.forEach { stat ->
            body.addView(layoutInflater.inflate(R.layout.row_stat, body, false).apply {
                findViewById<TextView>(R.id.label).setText(LABELS.getValue(stat.name))
                findViewById<TextView>(R.id.value).text = when (stat) {
                    is Day.Stat.Time -> stat.value?.format(MINUTE)
                    is Day.Stat.Length -> stat.value?.let(DayStats::formatGap)
                    is Day.Stat.Count -> stat.value?.toString()
                } ?: HistoryTab.NO_DATA
            })
        }
    }

    private fun showLengths(buckets: List<Day.Bucket>) {
        val body = findViewById<LinearLayout>(R.id.lengths_body)
        body.removeAllViews()
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

    private inner class TimelineAdapter(val entries: List<Day.Entry>) : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (entries[position] is Day.Entry.Use) 0 else 1
        override fun isEnabled(position: Int) = entries[position] is Day.Entry.Use

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val entry = entries[position]) {
                is Day.Entry.Use -> session(entry, convertView, parent)
                is Day.Entry.Break -> (convertView ?: layoutInflater.inflate(R.layout.row_break, parent, false)).apply {
                    val isLongest = entry.longest
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

        private fun session(entry: Day.Entry.Use, convertView: View?, parent: ViewGroup): View =
            (convertView ?: layoutInflater.inflate(R.layout.row_session, parent, false)).apply {
                val session = entry.session
                val start = session.start.format(MINUTE)
                val end = when {
                    session.open -> getString(R.string.session_now)
                    else -> session.end?.format(MINUTE)
                }
                val text = SpannableString(if (end == null) start else getString(R.string.session_range, start, end))
                if (entry.burst) text.setSpan(ForegroundColorSpan(getColor(R.color.burst)), 0, start.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (session.open) text.setSpan(ForegroundColorSpan(getColor(R.color.accent)), text.length - end!!.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                findViewById<TextView>(R.id.time).text = text

                val glance = entry.glance
                val length = session.length
                findViewById<TextView>(R.id.length).apply {
                    this.text = length?.let(DayStats::formatGap) ?: ""
                    setTextColor(getColor(if (glance) R.color.burst else R.color.text_muted))
                }
            }
    }

    companion object {
        private const val EXTRA_DAY = "day"
        private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val LABELS = mapOf(
            Day.Stat.Name.FIRST_UNLOCK to R.string.stat_first_unlock,
            Day.Stat.Name.LAST_LOCK to R.string.stat_last_lock,
            Day.Stat.Name.LONGEST_OFF to R.string.stat_longest_off,
            Day.Stat.Name.MEDIAN_OFF to R.string.stat_median_off,
            Day.Stat.Name.SCREEN_TIME to R.string.stat_screen_time,
            Day.Stat.Name.BURSTS to R.string.stat_bursts,
        )

        fun intent(context: Context, day: LocalDate): Intent =
            Intent(context, DayActivity::class.java).putExtra(EXTRA_DAY, day.toString())
    }
}
