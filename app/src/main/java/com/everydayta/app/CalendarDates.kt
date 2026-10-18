package com.everydayta.app

import java.time.LocalDate
import java.time.YearMonth

/** Monday-first week layout shared by the year and month calendar views. */
internal fun calendarWeekCount(month: YearMonth): Int {
    val leading = month.atDay(1).dayOfWeek.value - 1
    return (leading + month.lengthOfMonth() + 6) / 7
}

/** Date for a Monday-first calendar slot, or null for leading/trailing blanks. */
internal fun calendarDateAt(month: YearMonth, slot: Int): LocalDate? {
    require(slot in 0..41) { "Calendar slot must be in 0..41" }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val day = slot - leading + 1
    return if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
}
