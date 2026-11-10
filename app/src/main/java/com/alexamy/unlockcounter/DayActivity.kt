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
import java.time.format.DateTimeFormatter

class DayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val day = CounterStore(this).day(LocalDate.parse(intent.getStringExtra(EXTRA_DAY)))
        val times = day.times

        findViewById<TextView>(R.id.title).text =
            if (day.isToday) getString(R.string.today) else day.date.format(HistoryTab.DATE)

        findViewById<View>(R.id.day_empty).visibility = if (times.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.day_content).visibility = if (times.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.partial).apply {
            visibility = if (day.missing > 0) View.VISIBLE else View.GONE
            text = getString(R.string.times_partial, times.size, day.count)
        }

        findViewById<BarChart>(R.id.hours).apply {
            show(getString(R.string.hours_description), day.perHour.mapIndexed { hour, n -> BarChart.Bar(n, "$hour h") })
            highlight = day.hour
        }
        val stats = day.stats
        findViewById<TextView>(R.id.first).text = stats.first?.format(MINUTE) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.last).text = stats.last?.format(MINUTE) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.longest_gap).text = stats.longestGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.median_gap).text = stats.medianGap?.let(DayStats::formatGap) ?: HistoryTab.NO_DATA
        findViewById<TextView>(R.id.bursts).text = stats.bursts?.toString() ?: HistoryTab.NO_DATA
        findViewById<ListView>(R.id.times).adapter =
            ArrayAdapter(this, R.layout.row_time, times.map { it.format(TIME) })
    }

    companion object {
        private const val EXTRA_DAY = "day"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun intent(context: Context, day: LocalDate): Intent =
            Intent(context, DayActivity::class.java).putExtra(EXTRA_DAY, day.toString())
    }
}
