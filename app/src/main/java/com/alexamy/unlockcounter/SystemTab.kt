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
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** System tab: export and clear the unlock log, counting settings (burst window, glance limit, day start), reset the wallpaper settings. */
class SystemTab(
    private val activity: Activity,
    private val store: CounterStore,
    style: StyleStore,
    exportLog: () -> Unit,
    private val onDayStartChange: () -> Unit,
    onReset: () -> Unit,
) {
    private val exportSubtitle = activity.findViewById<TextView>(R.id.export_subtitle)
    private val burstSubtitle = activity.findViewById<TextView>(R.id.burst_subtitle)
    private val glanceSubtitle = activity.findViewById<TextView>(R.id.glance_subtitle)
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
        activity.findViewById<View>(R.id.burst_window).setOnClickListener {
            editMinutes(R.string.burst_window, store.burst) { store.burst = it }
        }
        activity.findViewById<View>(R.id.glance_limit).setOnClickListener {
            editMinutes(R.string.glance_limit, store.glance) { store.glance = it }
        }
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
        burstSubtitle.text = activity.getString(R.string.burst_subtitle, store.burst.toMinutes())
        glanceSubtitle.text = activity.getString(R.string.glance_subtitle, store.glance.toMinutes())
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

    /** A dialog for a whole number of minutes from 1 to 60. */
    private fun editMinutes(title: Int, current: Duration, save: (Duration) -> Unit) {
        val field = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toMinutes().toString())
            selectAll()
        }
        val box = FrameLayout(activity).apply {
            val side = (20 * resources.displayMetrics.density).toInt()
            setPadding(side, 0, side, 0)
            addView(field)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(R.string.burst_prompt)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = field.text.toString().toLongOrNull()
                if (minutes != null && minutes in 1..60) {
                    save(Duration.ofMinutes(minutes))
                    refresh()
                } else {
                    Toast.makeText(activity, R.string.burst_invalid, Toast.LENGTH_LONG).show()
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
