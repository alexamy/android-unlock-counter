package com.alexamy.unlockcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.time.OffsetDateTime

/** BOOT_COMPLETED comes once per boot, after the first unlock, which the wallpaper was not yet running to see. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val boot = OffsetDateTime.now().minusNanos(SystemClock.elapsedRealtimeNanos())
        CounterStore(context).addUnlockIfNoneSince(boot)
    }
}
