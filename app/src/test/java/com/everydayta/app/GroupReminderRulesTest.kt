package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GroupReminderRulesTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun tracker(id: Long) = Tracker(id, "Tracker $id", listOf("No", "Yes"), 0, 8, 0, true)
    private fun group(id: Long, members: List<Long>, paused: Boolean = false, reminder: Boolean = true) =
        TrackerGroup(id, "Group $id", 8, 0, reminder, members, paused = paused)

    @Test fun membershipSupersedesIndividualRemindersRegardlessOfGroupState() {
        val paused = group(1, listOf(1, 2), paused = true)
        val disabled = group(2, listOf(3), reminder = false)
        val active = group(3, listOf(4))
        assertEquals(setOf(1L, 2L, 3L, 4L), coveredIndividualTrackerIds(listOf(paused, disabled, active)))
        assertEquals(emptySet<Long>(), coveredIndividualTrackerIds(emptyList()))
        assertFalse(individualReminderAllowed(tracker(1), true))
        assertTrue(individualReminderAllowed(tracker(1), false))
        assertFalse(individualReminderAllowed(tracker(1).copy(paused = true), false))
        assertFalse(individualReminderAllowed(tracker(1).copy(reminder = false), false))
    }

    @Test fun pausingAndResumingGroupNeverReleasesConstituentReminders() {
        val active = group(1, listOf(1, 2))
        val paused = active.copy(paused = true)
        val allTrackers = listOf(tracker(1), tracker(2))
        assertEquals(setOf(1L, 2L), coveredIndividualTrackerIds(listOf(active)))
        assertEquals(setOf(1L, 2L), coveredIndividualTrackerIds(listOf(paused)))
        assertEquals(emptySet<Long>(), groupNotificationTrackerIds(paused, listOf(paused), allTrackers, day, emptyMap()))
        assertEquals(setOf(1L, 2L), groupNotificationTrackerIds(active, listOf(active), allTrackers, day, emptyMap()))
        assertEquals(setOf(1L, 2L), coveredIndividualTrackerIds(listOf(active)))
    }

    @Test fun disablingGroupReminderDoesNotReleaseItsMember() {
        val disabled = group(1, listOf(1), reminder = false)
        assertEquals(setOf(1L), coveredIndividualTrackerIds(listOf(disabled)))
        assertTrue(groupNotificationTrackerIds(disabled, listOf(disabled), listOf(tracker(1)), day, emptyMap()).isEmpty())
    }

    @Test fun overlappingGroupOwnershipDoesNotTransferWhenOwnerPausedOrDisabled() {
        val original = group(1, listOf(1, 2))
        val other = group(2, listOf(2, 3))
        val allTrackers = (1L..3L).map(::tracker)
        assertEquals(mapOf(1L to 1L, 2L to 1L, 3L to 2L), reminderOwnerGroupByTrackerId(listOf(other, original)))
        assertEquals(setOf(1L, 2L), groupNotificationTrackerIds(original, listOf(original, other), allTrackers, day, emptyMap()))
        assertEquals(setOf(3L), groupNotificationTrackerIds(other, listOf(original, other), allTrackers, day, emptyMap()))

        val paused = original.copy(paused = true)
        val disabled = original.copy(reminder = false)
        for (owner in listOf(paused, disabled)) {
            val groups = listOf(owner, other)
            assertEquals(setOf(1L, 2L, 3L), coveredIndividualTrackerIds(groups))
            assertEquals(emptySet<Long>(), groupNotificationTrackerIds(owner, groups, allTrackers, day, emptyMap()))
            assertEquals(setOf(3L), groupNotificationTrackerIds(other, groups, allTrackers, day, emptyMap()))
            assertEquals(1L, reminderOwnerGroupByTrackerId(groups)[2L])
        }
    }

    @Test fun groupDeletionOrMembershipRemovalRestoreIndividualOnlyWhenNoOwnerRemains() {
        val first = group(1, listOf(1, 2))
        val second = group(2, listOf(2, 3), paused = true)
        assertEquals(setOf(1L, 2L, 3L), coveredIndividualTrackerIds(listOf(first, second)))
        assertEquals(setOf(2L, 3L), coveredIndividualTrackerIds(listOf(second))) // group 1 deleted
        assertEquals(mapOf(2L to 2L, 3L to 2L), reminderOwnerGroupByTrackerId(listOf(second)))
        assertEquals(setOf(1L, 2L, 3L), coveredIndividualTrackerIds(listOf(first.copy(trackerIds = listOf(1)), second)))
        assertEquals(setOf(1L, 3L), coveredIndividualTrackerIds(listOf(first.copy(trackerIds = listOf(1)), second.copy(trackerIds = listOf(3)))))
        assertEquals(emptySet<Long>(), coveredIndividualTrackerIds(emptyList())) // both groups deleted
    }

    @Test fun scoresAndDueDaysStillSuppressNotifications() {
        val g = group(1, listOf(1, 2))
        val trackers = listOf(tracker(1), tracker(2).copy(paused = true))
        assertEquals(setOf(1L), groupNotificationTrackerIds(g, listOf(g), trackers, day, emptyMap()))
        assertEquals(emptySet<Long>(), groupNotificationTrackerIds(g, listOf(g), trackers, day, mapOf(1L to 2)))
        val mondaysOnly = tracker(1).copy(schedule = RecordingSchedule(RecordingSchedule.Kind.WEEKDAYS, weekdays = setOf(1)))
        assertEquals(emptySet<Long>(), groupNotificationTrackerIds(g, listOf(g), listOf(mondaysOnly), day.plusDays(1), emptyMap()))
    }

    @Test fun missingCountIsIndependentOfOtherTrackers() {
        val g = group(1, listOf(1, 2, 3))
        assertEquals(3, missingGroupTrackerCount(g, emptyMap()))
        assertEquals(2, missingGroupTrackerCount(g, mapOf(1L to 2, 4L to 1)))
        assertEquals(0, missingGroupTrackerCount(g, mapOf(1L to 1, 2L to 2, 3L to 3)))
    }
}
