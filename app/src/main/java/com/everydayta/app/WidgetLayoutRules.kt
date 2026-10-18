package com.everydayta.app

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Geometry for widget bitmaps. No Android dependency; can be tested on the JVM. */
object WidgetLayoutRules {
    /**
     * Estimate the Android widget's current bounds from AppWidgetOptions.
     * In portrait minWidth pairs with maxHeight; in landscape maxWidth pairs
     * with minHeight. Some launchers omit max values; fall back gracefully.
     */
    fun hostDimensions(minWidth: Int, minHeight: Int, maxWidth: Int,
                       maxHeight: Int, portrait: Boolean): Pair<Int, Int> {
        val width = if (!portrait && maxWidth > 0) maxWidth else minWidth
        val height = if (portrait && maxHeight > 0) maxHeight else minHeight
        return width.coerceIn(120, 700) to height.coerceIn(110, 900)
    }

    /** Aim for nearly square cells whatever the host's width:height ratio. */
    fun blockColumns(width: Int, height: Int, days: Int = 365): Int {
        require(width > 0 && height > 0 && days > 0)
        return sqrt(days.toDouble() * width / height).roundToInt().coerceIn(7, days)
    }

    /** Split the year into full-week bands when there is too much height for one strip. */
    fun weekBands(width: Int, height: Int, year: Int): Int {
        require(width > 0 && height > 0)
        val weeks = weekCount(year)
        // A strip may have a label; reserve the same small percentage for each band.
        return (1..4).minByOrNull { bands ->
            val cols = ceil(weeks.toDouble() / bands).toInt()
            val cw = width.toDouble() / cols
            val ch = (height * 0.90) / (7.0 * bands)
            abs(ln((cw / ch).coerceAtLeast(0.001))) + (bands - 1) * 0.08
        } ?: 1
    }

    /** Short, wide widgets cannot fit a 3x4 wall calendar; use a diary strip. */
    fun isCompactCalendar(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && (height.toFloat() / width < 0.35f || height < 85)

    /** 3 x 4 for portrait-like widgets, 4 x 3 when wide. */
    fun calendarColumns(width: Int, height: Int): Int = if (height.toFloat() / width.coerceAtLeast(1) >= 0.70f) 3 else 4

    fun weekCount(year: Int): Int {
        val jan = LocalDate.of(year, 1, 1)
        return (jan.dayOfWeek.value - 1 + jan.lengthOfYear() + 6) / 7
    }

    /** Matches Monday-first calendar columns. */
    fun weekCell(year: Int, dayOfYear: Int): Pair<Int, Int> {
        val jan = LocalDate.of(year, 1, 1)
        require(dayOfYear in 1..jan.lengthOfYear())
        val i = dayOfYear - 1 + jan.dayOfWeek.value - 1
        return Pair(i / 7, i % 7)
    }
}
