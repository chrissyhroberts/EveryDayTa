package com.everydayta.app

import java.time.LocalDate
import java.time.YearMonth

/** Helpers for each widget's independently selected, absolute calendar period. */
object WidgetPeriodRules {
    // Old widgets stored relative offsets; retain these functions for migration.
    fun yearFor(offset: Int, today: LocalDate = LocalDate.now()): Int =
        today.year + offset.coerceIn(-30, 30)

    fun moveYear(offset: Int, delta: Int): Int = (offset + delta).coerceIn(-30, 30)

    fun monthIndex(month: YearMonth): Int = month.year * 12 + month.monthValue - 1

    fun monthAt(index: Int): YearMonth =
        YearMonth.of(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)

    fun boundedYear(year: Int, delta: Int, current: Int = LocalDate.now().year): Int =
        (year + delta).coerceIn(current - 30, current + 30)

    fun boundedMonth(month: YearMonth, delta: Int,
                     current: YearMonth = YearMonth.now()): YearMonth {
        val lower = current.minusMonths(120)
        val upper = current.plusMonths(120)
        val wanted = month.plusMonths(delta.toLong())
        return when {
            wanted.isBefore(lower) -> lower
            wanted.isAfter(upper) -> upper
            else -> wanted
        }
    }
}
