package com.everydayta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CalendarDatesTest {
    @Test fun everyDayAppearsExactlyOnceInAllMonths() {
        for (year in 2024..2030) for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val days = (0 until calendarWeekCount(ym) * 7)
                .mapNotNull { calendarDateAt(ym, it) }
            assertEquals((1..ym.lengthOfMonth()).toList(), days.map { it.dayOfMonth })
        }
    }

    @Test fun weeksStartOnMondayAndLeaveLeadingBlanks() {
        val october = YearMonth.of(2026, 10) // Thursday
        assertEquals(5, calendarWeekCount(october))
        assertNull(calendarDateAt(october, 0))
        assertNull(calendarDateAt(october, 2))
        assertEquals(1, calendarDateAt(october, 3)?.dayOfMonth)
        assertTrue(calendarDateAt(october, 3)?.dayOfWeek?.value == 4)
    }

    @Test fun leapYearFebruaryIsNotTruncated() {
        val leapFebruary = YearMonth.of(2028, 2)
        assertEquals(29, (0 until calendarWeekCount(leapFebruary) * 7)
            .count { calendarDateAt(leapFebruary, it) != null })
    }
}
