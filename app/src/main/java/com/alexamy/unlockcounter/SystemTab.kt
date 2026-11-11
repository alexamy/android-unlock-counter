package com.alexamy.unlockcounter

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** System tab: export and clear the unlock log, day and sessions settings, reset the wallpaper settings. */
class SystemTab(
    private val activity: Activity,
    private val store: CounterStore,
    style: StyleStore,
    exportLog: () -> Unit,
    private val onDayStartChange: () -> Unit,
    onReset: () -> Unit,
) {
    private val exportSubtitle = activity.findViewById<TextView>(R.id.export_subtitle)
    private val durations = listOf(
        DurationRow(DurationSetting.BURST, R.id.burst_window, R.id.burst_subtitle, R.string.burst_window, R.string.burst_subtitle),
        DurationRow(DurationSetting.GLANCE, R.id.glance_limit, R.id.glance_subtitle, R.string.glance_limit, R.string.glance_subtitle),
        DurationRow(DurationSetting.OPEN_LIMIT, R.id.open_limit, R.id.open_limit_subtitle, R.string.open_limit, R.string.open_limit_subtitle),
    )
    private val dayStartSubtitle = activity.findViewById<TextView>(R.id.day_start_subtitle)

    init {
        activity.findViewById<View>(R.id.export_log).setOnClickListener { exportLog() }
        activity.findViewById<View>(R.id.clear_log).setOnClickListener {
            confirm(R.string.clear_log_confirm) {
                store.clearLog()
                refresh()
                Toast.makeText(activity, R.string.log_cleared, Toast.LENGTH_LONG).show()
            }
        }
        durations.forEach { row -> activity.findViewById<View>(row.row).setOnClickListener { editDuration(row) } }
        activity.findViewById<View>(R.id.day_start).setOnClickListener { editDayStart() }
        activity.findViewById<View>(R.id.reset_settings).setOnClickListener {
            confirm(R.string.reset_settings_confirm) {
                style.reset()
                onReset()
            }
        }
    }

    fun refresh() {
        exportSubtitle.text = activity.getString(R.string.export_subtitle, store.logEntries())
        durations.forEach { row ->
            activity.findViewById<TextView>(row.subtitle).text = activity.getString(row.text, row.setting.units(store[row.setting]))
        }
        dayStartSubtitle.text = activity.getString(R.string.day_start_subtitle, store.dayStart.format(TIME))
    }

    private fun editDayStart() {
        val start = store.dayStart
        TimePickerDialog(activity, { _, hour, minute ->
            store.dayStart = LocalTime.of(hour, minute)
            refresh()
            onDayStartChange()
        }, start.hour, start.minute, DateFormat.is24HourFormat(activity)).show()
    }

    /** A System tab row for a [DurationSetting]: the row, its subtitle view, and the title and subtitle texts. */
    private class DurationRow(val setting: DurationSetting, val row: Int, val subtitle: Int, val title: Int, val text: Int)

    /** A dialog for a whole number of the setting's unit within its range. */
    private fun editDuration(row: DurationRow) {
        val setting = row.setting
        val hours = setting.unit == ChronoUnit.HOURS
        val range = setting.range
        val field = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(setting.units(store[setting]).toString())
            selectAll()
        }
        val box = FrameLayout(activity).apply {
            val side = (20 * resources.displayMetrics.density).toInt()
            setPadding(side, 0, side, 0)
            addView(field)
        }
        AlertDialog.Builder(activity)
            .setTitle(row.title)
            .setMessage(activity.getString(if (hours) R.string.hours_prompt else R.string.minutes_prompt, range.first, range.last))
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = field.text.toString().toLongOrNull()
                if (value != null && value in range) {
                    store[setting] = setting.of(value)
                    refresh()
                } else {
                    val invalid = activity.getString(if (hours) R.string.hours_invalid else R.string.minutes_invalid, range.first, range.last)
                    Toast.makeText(activity, invalid, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirm(message: Int, action: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
