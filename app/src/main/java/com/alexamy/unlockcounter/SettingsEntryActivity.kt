package com.alexamy.unlockcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Target of the wallpaper picker's "Settings": opens MainActivity on the Settings tab, also when it is already running. */
class SettingsEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
