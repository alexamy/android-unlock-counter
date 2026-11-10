package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class DayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val store = CounterStore(this)
        val day = LocalDate.parse(intent.getStringExtra(EXTRA_DAY))
        val now = OffsetDateTime.now()
        val isToday = day == now.toLocalDate()
        val times = store.unlockTimes(day)
        val count = store.dayCount(day)

        findViewById<TextView>(R.id.title).text =
            if (isToday) getString(R.string.today) else day.format(HistoryTab.DATE)

        findViewById<View>(R.id.day_empty).visibility = if (times.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.day_content).visibility = if (times.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.partial).apply {
            visibility = if (count != null && times.size < count) View.VISIBLE else View.GONE
            text = getString(R.string.times_partial, times.size, count)
        }

        val perHour = times.groupingBy { it.hour }.eachCount()
        val hours = (0..23).map { perHour[it] ?: 0 }
        findViewById<BarChart>(R.id.hours).apply {
            values = hours
            highlight = if (isToday) now.hour else null
            contentDescription = getString(R.string.hours_description, hours.withIndex().joinToString { (hour, n) -> "$hour: $n" })
        }
        val stats = DayStats.of(times)
        findViewById<TextView>(R.id.first).text = stats.first?.format(MINUTE) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.last).text = stats.last?.format(MINUTE) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.longest_gap).text = stats.longestGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.median_gap).text = stats.medianGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.bursts).text = stats.bursts?.toString() ?: HistoryTab.NO_DATA
        findViewById<ListView>(R.id.times).adapter =
            ArrayAdapter(this, R.layout.row_time, times.sortedByDescending { it.toInstant() }.map { it.format(TIME) })
    }

    companion object {
        private const val EXTRA_DAY = "day"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun intent(context: Context, day: LocalDate): Intent =
            Intent(context, DayActivity::class.java).putExtra(EXTRA_DAY, day.toString())
    }
}
