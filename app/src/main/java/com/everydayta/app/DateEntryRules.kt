package com.everydayta.app

import java.time.LocalDate

/** Entry/edit/delete permission for manual check-ins; importing a backup is different. */
fun dateEntryAllowed(
    date: LocalDate,
    today: LocalDate,
    allowPastEntries: Boolean,
    allowFutureEntries: Boolean
): Boolean = when {
    date.isBefore(today) -> allowPastEntries
    date.isAfter(today) -> allowFutureEntries
    else -> true
}
