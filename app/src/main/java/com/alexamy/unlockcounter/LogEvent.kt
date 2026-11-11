package com.alexamy.unlockcounter

import java.time.OffsetDateTime

/** One line of unlocks.log: a time and whether it was a lock. */
data class LogEvent(val time: OffsetDateTime, val isLock: Boolean) {
    val line: String get() = "$time ${if (isLock) LOCK else UNLOCK}\n"

    companion object {
        private const val UNLOCK = "unlock"
        private const val LOCK = "lock"

        /** A line without a type is an unlock, as older versions wrote it; an unreadable line is null. */
        fun parse(line: String): LogEvent? {
            val parts = line.split(' ')
            val time = runCatching { OffsetDateTime.parse(parts[0]) }.getOrNull() ?: return null
            return LogEvent(time, parts.getOrNull(1) == LOCK)
        }
    }
}
