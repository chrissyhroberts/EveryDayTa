package com.everydayta.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PausedRulesTest {
    @Test fun pauseSuppressesDueAndMissingWithoutLosingScore() {
        val t = Tracker(15, "Energy", listOf("Low", "High"), 0, 8, 0, true)
        val day = LocalDate.of(2026, 10, 19)
        assertTrue(dueAndMissing(t, day, emptyMap()))
        assertFalse(dueAndMissing(t.copy(paused = true), day, emptyMap()))
        assertFalse(dueAndMissing(t, day, mapOf(t.id to 2)))
    }
    @Test fun pausedMemberDoesNotEnterGroupNotification() {
        val active = Tracker(1, "Mood", listOf("Low", "High"), 0, 8, 0, true)
        val paused = Tracker(2, "Pain", listOf("Low", "High"), 0, 8, 0, true, paused = true)
        val group = TrackerGroup(1, "Morning", 8, 0, true, listOf(1, 2))
        val day = LocalDate.of(2026, 10, 19)
        assertEquals(setOf(1L), groupNotificationTrackerIds(group, listOf(group), listOf(active,paused), day, emptyMap()))
        assertEquals(1, missingGroupTrackerCount(group, listOf(active, paused), day, emptyMap()))
        assertEquals(emptySet<Long>(), groupNotificationTrackerIds(group, listOf(group), listOf(active.copy(paused=true),paused), day, emptyMap()))
    }
}
