package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GroupReminderSlotsTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val trackers = (1L..4L).map {
        Tracker(it, "Medication $it", ScaleSemantics.doneNotDoneLabels, 0, 8, 0, true)
    }
    private val group = TrackerGroup(1, "Medication", 8, 0, true,
        listOf(1, 2, 3, 4), reminderSlots = listOf(
            GroupReminderSlot("am", "AM", 8, 0, true, listOf(1, 2), "Take AM medicine"),
            GroupReminderSlot("pm", "PM", 20, 0, true, listOf(3, 4), "Take PM medicine")
        ), multipleReminders = true)

    @Test fun amAndPmAreIndependentAndRespectRecordedScores() {
        val am = group.reminderSlots.first()
        val pm = group.reminderSlots.last()
        assertEquals(setOf(1L, 2L), groupNotificationTrackerIds(group, am, listOf(group), trackers, today, emptyMap()))
        assertEquals(setOf(3L, 4L), groupNotificationTrackerIds(group, pm, listOf(group), trackers, today, emptyMap()))
        val recordedAm = mapOf(1L to 2, 2L to 1)
        assertTrue(groupNotificationTrackerIds(group, am, listOf(group), trackers, today, recordedAm).isEmpty())
        assertEquals(setOf(3L, 4L), groupNotificationTrackerIds(group, pm, listOf(group), trackers, today, recordedAm))
    }

    @Test fun pausingGroupCannotReactivateIndividualOrOtherGroup() {
        val paused = group.copy(paused = true)
        val other = TrackerGroup(2, "Another", 11, 0, true, listOf(2, 4))
        assertEquals(setOf(1L, 2L, 3L, 4L), coveredIndividualTrackerIds(listOf(paused, other)))
        assertFalse(individualReminderAllowed(trackers[1], true))
        assertTrue(groupNotificationTrackerIds(paused, paused.reminderSlots.first(), listOf(paused, other), trackers, today, emptyMap()).isEmpty())
        assertTrue(groupNotificationTrackerIds(other, other.effectiveReminderSlots().single(), listOf(paused, other), trackers, today, emptyMap()).isEmpty())
    }

    @Test fun reminderOwnershipRemainsStableWhenFirstSlotDisabled() {
        val disabled = group.copy(reminderSlots = group.reminderSlots.map {
            if (it.id == "am") it.copy(enabled = false) else it
        })
        assertTrue(groupNotificationTrackerIds(disabled, disabled.reminderSlots.first(), listOf(disabled), trackers, today, emptyMap()).isEmpty())
        assertEquals(setOf(3L, 4L), groupNotificationTrackerIds(disabled, disabled.reminderSlots.last(), listOf(disabled), trackers, today, emptyMap()))
        assertEquals("am", reminderOwnerSlotByTrackerId(disabled)[1L])
    }

    @Test fun unassignedMembersRemainGroupOwnedButSilent() {
        val partiallyAssigned = group.copy(reminderSlots = listOf(group.reminderSlots.first()))
        assertEquals(setOf(1L, 2L, 3L, 4L), coveredIndividualTrackerIds(listOf(partiallyAssigned)))
        assertEquals(setOf(1L, 2L), groupNotificationTrackerIds(partiallyAssigned,
            partiallyAssigned.reminderSlots.first(), listOf(partiallyAssigned), trackers, today, emptyMap()))
        assertNull(reminderOwnerSlotByTrackerId(partiallyAssigned)[3L])
        assertTrue(group.copy(reminderSlots = emptyList()).effectiveReminderSlots().isEmpty())
    }

    @Test fun recordedNotDoneIsNotTheSameAsNoEntry() {
        assertEquals(ScaleSemantics.DONE_NOT_DONE, ScaleSemantics.inferLegacy(ScaleSemantics.doneNotDoneLabels))
        assertEquals("Not done", ScaleSemantics.displayLabel(ScaleSemantics.DONE_NOT_DONE, ScaleSemantics.doneNotDoneLabels, 1))
        assertEquals("Done", ScaleSemantics.displayLabel(ScaleSemantics.DONE_NOT_DONE, ScaleSemantics.doneNotDoneLabels, 2))
        assertTrue(dueAndMissing(trackers[0], today, emptyMap()))
        assertFalse(dueAndMissing(trackers[0], today, mapOf(1L to 1)))
        assertFalse(dueAndMissing(trackers[0], today, mapOf(1L to 2)))
    }
}
