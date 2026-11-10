package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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

        findViewById<TextView>(R.id.title).text =
            if (day.isToday) getString(R.string.today) else day.date.format(HistoryTab.DATE)

        findViewById<View>(R.id.day_empty).visibility = if (times.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.day_content).visibility = if (times.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.partial).apply {
            visibility = if (day.missing > 0) View.VISIBLE else View.GONE
            text = getString(R.string.times_partial, times.size, day.count)
        }

        val first = stats.first?.format(MINUTE) ?: HistoryTab.NO_DATA
        val last = stats.last?.format(MINUTE) ?: HistoryTab.NO_DATA
        val longest = stats.longestGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.first).text = first
        findViewById<TextView>(R.id.last).text = last
        findViewById<TextView>(R.id.longest_gap).text = longest
        findViewById<TextView>(R.id.median_gap).text = stats.medianGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.bursts).text = stats.bursts?.toString() ?: HistoryTab.NO_DATA

        val timeline = day.timeline
        val strip = findViewById<DayStrip>(R.id.strip)
        val list = findViewById<ListView>(R.id.timeline)
        strip.times = times
        strip.contentDescription = getString(R.string.strip_description, first, last, longest)
        list.adapter = TimelineAdapter(timeline, stats.longestGap)
        list.setOnItemClickListener { _, _, position, _ -> strip.lit = (timeline[position] as Day.Entry.Unlocks).indices }
        strip.onTap = { index ->
            val position = timeline.indexOfFirst { it is Day.Entry.Unlocks && index in it.indices }
            list.setItemChecked(position, true)
            list.smoothScrollToPosition(position)
            strip.lit = (timeline[position] as Day.Entry.Unlocks).indices
        }
    }

    private inner class TimelineAdapter(val entries: List<Day.Entry>, val longest: Duration?) : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (entries[position] is Day.Entry.Unlocks) 0 else 1
        override fun isEnabled(position: Int) = entries[position] is Day.Entry.Unlocks

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val entry = entries[position]) {
                is Day.Entry.Unlocks -> (convertView ?: layoutInflater.inflate(R.layout.row_unlock, parent, false)).apply {
                    findViewById<TextView>(R.id.time).text = entry.first.format(MINUTE)
                    findViewById<TextView>(R.id.note).apply {
                        val burst = entry.count > 1
                        text = if (burst) getString(R.string.timeline_burst, entry.count, DayStats.formatGap(entry.span)) else getString(R.string.timeline_unlock)
                        setTextColor(getColor(if (burst) R.color.burst else R.color.text_muted))
                    }
                }
                is Day.Entry.Break -> (convertView ?: layoutInflater.inflate(R.layout.row_break, parent, false)).apply {
                    val isLongest = entry.length == longest
                    val color = getColor(if (isLongest) R.color.accent else R.color.bar_past)
                    // Height grows with the log of the break, so a 6 h night does not fill the screen.
                    minimumHeight = (resources.displayMetrics.density * (20 + 22 * ln(1.0 + entry.length.toMinutes() / 15.0) / ln(2.0))).toInt()
                    findViewById<View>(R.id.rail).setBackgroundColor(color)
                    findViewById<TextView>(R.id.label).apply {
                        text = getString(if (isLongest) R.string.timeline_break_longest else R.string.timeline_break, DayStats.formatGap(entry.length))
                        setTextColor(if (isLongest) color else getColor(R.color.text_muted))
                    }
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
