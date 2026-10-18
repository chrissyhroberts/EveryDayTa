package com.everydayta.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetPeriodRulesTest {
    private val anchor = LocalDate.of(2026, 10, 18)
    @Test fun yearsAdvanceAndRewindIndependently() {
        assertEquals(2026, WidgetPeriodRules.yearFor(0, anchor))
        assertEquals(2025, WidgetPeriodRules.yearFor(WidgetPeriodRules.moveYear(0, -1), anchor))
        assertEquals(2027, WidgetPeriodRules.yearFor(WidgetPeriodRules.moveYear(0, 1), anchor))
        assertEquals(2026, WidgetPeriodRules.yearFor(WidgetPeriodRules.moveYear(1, -1), anchor))
    }
    @Test fun offsetsStayWithinSupportedRange() {
        assertEquals(30, WidgetPeriodRules.moveYear(30, 1))
        assertEquals(-30, WidgetPeriodRules.moveYear(-30, -1))
        assertEquals(2036, WidgetPeriodRules.yearFor(10, anchor))
    }
    @Test fun absoluteMonthSelectionDoesNotJumpAtMonthBoundary() {
        val october = java.time.YearMonth.of(2026, 10)
        val saved = WidgetPeriodRules.monthIndex(october)
        assertEquals(october, WidgetPeriodRules.monthAt(saved))
        assertEquals("2026-09", WidgetPeriodRules.monthAt(saved - 1).toString())
        assertEquals("2026-11", WidgetPeriodRules.monthAt(saved + 1).toString())
    }
    @Test fun yearSelectionDoesNotDependOnCurrentDate() {
        assertEquals(2025, WidgetPeriodRules.boundedYear(2026, -1, 2026))
        assertEquals(2025, WidgetPeriodRules.boundedYear(2025, 0, 2027))
    }
    @Test fun groupNavigationStillUsesCalendarMonths() {
        assertEquals("2026-09", GroupMonthRules.monthFor(-1, anchor).toString())
        assertEquals("2026-11", GroupMonthRules.monthFor(1, anchor).toString())
    }
}
