package com.alexamy.unlockcounter

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** History tab: today's count, one week of bars with its average (swipe or arrows for other weeks), then every past day. */
class HistoryTab(private val activity: Activity, private val store: CounterStore) {
    private val todayCount = view<TextView>(R.id.today_count)
    private val average = view<TextView>(R.id.average)
    private val bars = view<BarChart>(R.id.bars)
    private val weekLabel = view<TextView>(R.id.week_label)
    private val prevWeek = view<View>(R.id.prev_week)
    private val nextWeek = view<View>(R.id.next_week)
    private val empty = view<View>(R.id.history_empty)
    private val list = view<ListView>(R.id.history)

    private var weeks: List<Week> = emptyList()
    private var shown = 0

    init {
        todayCount.typeface = NumberFont.SIX_CAPS.load(activity.assets)
        val weekdays = view<ViewGroup>(R.id.weekdays)
        DayOfWeek.entries.forEachIndexed { i, day -> (weekdays.getChildAt(i) as TextView).text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        prevWeek.setOnClickListener { showWeek(shown - 1, -1) }
        nextWeek.setOnClickListener { showWeek(shown + 1, 1) }
        bars.onSwipe = { showWeek(shown + it, it) }
    }

    fun refresh() {
        val rows = store.historyRows()
        val kept = weeks.getOrNull(shown)?.monday
        weeks = store.weeks()
        val today = weeks.last().days.last().first
        todayCount.text = store.todayCount().toString()
        todayCount.setOnClickListener { openDay(today) }
        showWeek(weeks.indexOfFirst { it.monday == kept }.takeIf { it >= 0 } ?: weeks.lastIndex, 0)
        list.setOnItemClickListener { _, _, position, _ -> openDay(rows[position].first) }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        list.adapter = object : ArrayAdapter<Pair<LocalDate, Int?>>(activity, 0, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: activity.layoutInflater.inflate(R.layout.row_history, parent, false)
                val (day, count) = rows[position]
                row.findViewById<TextView>(R.id.day).text = day.format(DATE)
                row.findViewById<TextView>(R.id.count).apply {
                    text = count?.toString() ?: NO_DATA
                    setTextColor(activity.getColor(if (count == null) R.color.text_muted else R.color.text))
                }
                return row
            }
        }
    }

    /** Shows week [index] of [weeks]; [direction] is the slide: +1 from the right, -1 from the left, 0 none. */
    private fun showWeek(index: Int, direction: Int) {
        if (index !in weeks.indices) return
        shown = index
        val week = weeks[index]
        val today = weeks.last().days.last().first
        val sunday = week.monday.plusDays(6)
        weekLabel.text = if (week.monday.month == sunday.month) "${week.monday.dayOfMonth} – ${sunday.format(SHORT_DATE)}"
        else "${week.monday.format(SHORT_DATE)} – ${sunday.format(SHORT_DATE)}"
        average.text = week.average?.roundToInt()?.toString() ?: NO_DATA
        bars.show(activity.getString(R.string.week_description, weekLabel.text), week.days.map { (day, count) ->
            BarChart.Bar(count, if (day == today) activity.getString(R.string.today_lower) else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
        }, slots = 7)
        bars.highlight = week.days.indexOfFirst { it.first == today }.takeIf { it >= 0 }
        bars.onBarClick = { openDay(week.days[it].first) }
        prevWeek.isEnabled = index > 0
        prevWeek.alpha = if (prevWeek.isEnabled) 1f else DISABLED
        nextWeek.isEnabled = index < weeks.lastIndex
        nextWeek.alpha = if (nextWeek.isEnabled) 1f else DISABLED
        if (direction != 0) {
            bars.translationX = direction * bars.width / 3f
            bars.alpha = 0f
            bars.animate().translationX(0f).alpha(1f).setDuration(180).start()
        }
    }

    private fun openDay(day: LocalDate) = activity.startActivity(DayActivity.intent(activity, day))

    private fun <T : View> view(id: Int): T = activity.findViewById(id)

    companion object {
        private const val DISABLED = 0.3f
        const val NO_DATA = "—"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
        private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
