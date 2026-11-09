package com.alexamy.unlockcounter

import java.time.LocalDate

/** Past days from yesterday back to the oldest record, newest first; null count = no data that day. */
fun historyRows(records: Map<LocalDate, Int>, today: LocalDate): List<Pair<LocalDate, Int?>> {
    val oldest = records.keys.minOrNull() ?: return emptyList()
    return generateSequence(today.minusDays(1)) { it.minusDays(1) }
        .takeWhile { it >= oldest }
        .map { it to records[it] }
        .toList()
}
