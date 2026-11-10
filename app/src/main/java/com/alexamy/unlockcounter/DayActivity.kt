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

/** One day: unlocks per hour and the logged unlock times, newest first. */
class DayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val store = CounterStore(this)
        val day = LocalDate.parse(intent.getStringExtra(EXTRA_DAY))
        val now = OffsetDateTime.now()
        val times = store.unlockTimes(day)
        val count = store.dayCount(day)

        findViewById<TextView>(R.id.title).text =
            if (day == now.toLocalDate()) getString(R.string.today) else day.format(HistoryTab.DATE)

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
            highlight = if (day == now.toLocalDate()) now.hour else null
            contentDescription = getString(R.string.hours_description, hours.withIndex().joinToString { (hour, n) -> "$hour: $n" })
        }
        findViewById<ListView>(R.id.times).adapter =
            ArrayAdapter(this, R.layout.row_time, times.reversed().map { it.format(TIME) })
    }

    companion object {
        private const val EXTRA_DAY = "day"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun intent(context: Context, day: LocalDate): Intent =
            Intent(context, DayActivity::class.java).putExtra(EXTRA_DAY, day.toString())
    }
}
