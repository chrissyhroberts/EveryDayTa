package com.everydayta.app

import java.time.LocalDate
import java.time.YearMonth

/** Month navigation and screen geometry independent of Android. */
object GroupMonthRules {
    fun monthFor(offset: Int, today: LocalDate = LocalDate.now()): YearMonth =
        YearMonth.from(today).plusMonths(offset.coerceIn(-120, 120).toLong())

    fun move(offset: Int, delta: Int): Int = (offset + delta).coerceIn(-120, 120)

    /** Reserve a predictable name column and leave room for 28–31 date cells. */
    fun labelWidth(imageWidth: Int): Float =
        (imageWidth * .28f).coerceIn(52f, 150f).coerceAtMost(imageWidth * .43f)

    /** Prefer legible rows to silently clipping names. Overflow appears as +N more. */
    fun visibleRows(count: Int, imageHeight: Int): Int {
        if (count <= 0) return 0
        val available = (imageHeight - 22).coerceAtLeast(18)
        return (available / 18).coerceAtLeast(1).coerceAtMost(count)
    }
}
