package com.everydayta.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateEntryRulesTest {
    private val today = LocalDate.of(2026, 10, 18)
    @Test fun todayIsAlwaysEditable() {
        assertTrue(dateEntryAllowed(today, today, false, false))
    }
    @Test fun defaultsAllowPastNotFuture() {
        assertTrue(dateEntryAllowed(today.minusDays(1), today, true, false))
        assertFalse(dateEntryAllowed(today.plusDays(1), today, true, false))
    }
    @Test fun lockedPastCanBeReadButNotChanged() {
        assertFalse(dateEntryAllowed(today.minusYears(1), today, false, false))
    }
    @Test fun optionalFutureEntries() {
        assertTrue(dateEntryAllowed(today.plusYears(1), today, false, true))
    }
}
