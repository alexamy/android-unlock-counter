package com.alexamy.unlockcounter

import java.time.LocalDate

/**
 * A week from [monday]. [days] run from Monday up to Sunday or today, whichever is first; a null count means no data.
 * [average] covers the finished days with data, and is null when there are none.
 */
data class Week(val monday: LocalDate, val days: List<Pair<LocalDate, Int?>>, val average: Double?)
