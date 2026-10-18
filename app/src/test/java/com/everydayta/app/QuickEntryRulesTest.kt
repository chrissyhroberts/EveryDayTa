package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuickEntryRulesTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun tracker(id: Long, labels: List<String>, type: String = ScaleSemantics.CUSTOM) =
        Tracker(id, "Tracker $id", labels, 0, 8, 0, true, scaleType = type)
    private fun group(ids: List<Long>, paused: Boolean = false) =
        TrackerGroup(8, "Medication", 8, 0, true, ids, paused = paused,
            reminderSlots = listOf(
                GroupReminderSlot("am", "AM", 8, 0, true, ids.take(2)),
                GroupReminderSlot("pm", "PM", 20, 0, true, ids.drop(2))
            ), multipleReminders = true)

    @Test fun doneAndNotDoneAreDistinctFromMissing() {
        val t = tracker(1, ScaleSemantics.doneNotDoneLabels, ScaleSemantics.DONE_NOT_DONE)
        assertEquals(1, QuickEntryRules.parse(t, "Not done"))
        assertEquals(2, QuickEntryRules.parse(t, "Done"))
        assertNull(QuickEntryRules.parse(t, ""))
        assertEquals("Not done", QuickEntryRules.display(t, 1))
    }

    @Test fun zeroToTenRepliesMapToOneBasedStorage() {
        val t = tracker(1, ScaleSemantics.zeroToTenLabels, ScaleSemantics.ZERO_TO_TEN)
        (0..10).forEach { n ->
            assertEquals(n + 1, QuickEntryRules.parse(t, "$n"))
            assertEquals("$n", QuickEntryRules.display(t, n + 1))
        }
        assertNull(QuickEntryRules.parse(t, "11"))
        assertNull(QuickEntryRules.parse(t, "-1"))
    }

    @Test fun likertAllowsCaseInsensitiveLabelsOrIndices() {
        val t = tracker(1, ScaleSemantics.likertLabels, ScaleSemantics.LIKERT)
        assertEquals(1, QuickEntryRules.parse(t, "VERY BAD"))
        assertEquals(3, QuickEntryRules.parse(t, "neutral"))
        assertEquals(5, QuickEntryRules.parse(t, "5"))
        assertNull(QuickEntryRules.parse(t, "6"))
    }

    @Test fun amAndPmOnlyReturnTheirOwnedMissingMembers() {
        val t = (1L..4L).map { tracker(it, ScaleSemantics.doneNotDoneLabels, ScaleSemantics.DONE_NOT_DONE) }
        val g = group(t.map { it.id })
        val am = g.reminderSlots[0]; val pm = g.reminderSlots[1]
        assertEquals(listOf(1L, 2L), QuickEntryRules.pending(g, am, listOf(g), t, today, emptyMap()).map { it.id })
        assertEquals(listOf(3L, 4L), QuickEntryRules.pending(g, pm, listOf(g), t, today, mapOf(1L to 2)).map { it.id })
        assertEquals(listOf(2L), QuickEntryRules.pending(g, am, listOf(g), t, today, mapOf(1L to 1)).map { it.id })
        assertEquals(emptyList<Long>(), QuickEntryRules.pending(g, am, listOf(g), t, today, mapOf(1L to 1, 2L to 2)).map { it.id })
    }

    @Test fun pausedGroupNeverReleasesChildrenIntoIndividualReminders() {
        val t = tracker(1L, ScaleSemantics.doneNotDoneLabels, ScaleSemantics.DONE_NOT_DONE)
        val g = group(listOf(1L), paused = true)
        assertTrue(QuickEntryRules.pending(g, g.reminderSlots[0], listOf(g), listOf(t), today, emptyMap()).isEmpty())
        assertTrue(QuickEntryRules.pending(null, null, listOf(g), listOf(t), today, emptyMap(), 1L).isEmpty())
    }

    @Test fun groupOwnershipSuppressesIndividualAndOverlappingGroup() {
        val t = tracker(1L, ScaleSemantics.doneNotDoneLabels, ScaleSemantics.DONE_NOT_DONE)
        val first = group(listOf(1L)).copy(id = 1L)
        val second = group(listOf(1L)).copy(id = 2L)
        assertEquals(listOf(1L), QuickEntryRules.pending(first, first.reminderSlots[0],
            listOf(first, second), listOf(t), today, emptyMap()).map { it.id })
        assertTrue(QuickEntryRules.pending(second, second.reminderSlots[0],
            listOf(first, second), listOf(t), today, emptyMap()).isEmpty())
    }
}
