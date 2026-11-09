package com.alexamy.unlockcounter

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.TextView
import android.widget.Toast

/** System tab: export and clear the unlock log, reset the wallpaper settings. */
class SystemTab(
    private val activity: Activity,
    private val store: CounterStore,
    style: StyleStore,
    exportLog: () -> Unit,
    onReset: () -> Unit,
) {
    private val exportSubtitle = activity.findViewById<TextView>(R.id.export_subtitle)

    init {
        activity.findViewById<View>(R.id.export_log).setOnClickListener { exportLog() }
        activity.findViewById<View>(R.id.clear_log).setOnClickListener {
            confirm(R.string.clear_log_confirm) {
                store.clearLog()
                refresh()
                Toast.makeText(activity, R.string.log_cleared, Toast.LENGTH_LONG).show()
            }
        }
        activity.findViewById<View>(R.id.reset_settings).setOnClickListener {
            confirm(R.string.reset_settings_confirm) {
                style.reset()
                onReset()
            }
        }
    }

    fun refresh() {
        exportSubtitle.text = activity.getString(R.string.export_subtitle, store.logEntries())
    }

    private fun confirm(message: Int, action: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
