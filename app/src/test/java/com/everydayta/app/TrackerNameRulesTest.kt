package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test

class TrackerNameRulesTest {
    @Test fun matchingNamesIgnoreCaseAndWhitespace() {
        assertFalse(TrackerNameRules.isUnique(" pain ", listOf(1L to "PAIN")))
        assertTrue(TrackerNameRules.isUnique("Other", listOf(1L to "PAIN")))
        assertTrue(TrackerNameRules.isUnique(" Pain ", listOf(1L to "PAIN"), 1L))
    }
    @Test fun backupNameValidationRejectsDuplicates() {
        assertTrue(TrackerNameRules.duplicates(listOf("Pain", " pain ")))
        assertFalse(TrackerNameRules.duplicates(listOf("Pain", "Mood")))
    }
}
