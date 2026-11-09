package com.alexamy.unlockcounter

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** History tab: today's count, the 14-day bars and average, then every past day. */
class HistoryTab(private val activity: Activity, private val store: CounterStore) {
    private val todayCount = view<TextView>(R.id.today_count)
    private val average = view<TextView>(R.id.average)
    private val bars = view<BarChart>(R.id.bars)
    private val barsStart = view<TextView>(R.id.bars_start)
    private val empty = view<View>(R.id.history_empty)
    private val list = view<ListView>(R.id.history)

    init {
        todayCount.typeface = NumberFont.SIX_CAPS.load(activity.assets)
    }

    fun refresh() {
        val rows = store.historyRows()
        val window = store.lastDays(BAR_DAYS)
        todayCount.text = window.last().second.toString()
        average.text = store.dailyAverage(BAR_DAYS)?.roundToInt()?.toString() ?: "—"
        bars.values = window.map { it.second }
        bars.contentDescription = activity.getString(R.string.chart_description, window.mapIndexed { i, (_, count) ->
            if (i == window.lastIndex) activity.getString(R.string.today_value, count) else count?.toString() ?: activity.getString(R.string.no_data)
        }.joinToString())
        barsStart.text = window.first().first.format(SHORT_DATE)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        list.adapter = object : ArrayAdapter<Pair<LocalDate, Int?>>(activity, 0, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: activity.layoutInflater.inflate(R.layout.row_history, parent, false)
                val (day, count) = rows[position]
                row.findViewById<TextView>(R.id.day).text = day.format(DATE)
                row.findViewById<TextView>(R.id.count).apply {
                    text = count?.toString() ?: "—"
                    setTextColor(activity.getColor(if (count == null) R.color.text_muted else R.color.text))
                }
                return row
            }
        }
    }

    private fun <T : View> view(id: Int): T = activity.findViewById(id)

    private companion object {
        const val BAR_DAYS = 14
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
