package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GroupMonthRulesTest {
    @Test fun arrowsHandleYearsAndStayWithinTenYearBounds() {
        assertEquals("2027-01", GroupMonthRules.monthFor(1,LocalDate.of(2026,12,20)).toString())
        assertEquals("2025-12", GroupMonthRules.monthFor(-1,LocalDate.of(2026,1,20)).toString())
        assertEquals(120, GroupMonthRules.move(120, 1))
        assertEquals(-120, GroupMonthRules.move(-120,-1))
    }
    @Test fun nameColumnAndOverflowArePredictable() {
        assertTrue(GroupMonthRules.labelWidth(500f.toInt()) >= 52f)
        assertTrue(GroupMonthRules.visibleRows(12,90) < 12)
        assertEquals(4, GroupMonthRules.visibleRows(4,400))
    }
}
