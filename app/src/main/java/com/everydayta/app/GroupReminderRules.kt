package com.everydayta.app

import java.time.LocalDate

/**
 * Membership, not notification enablement, owns a tracker reminder.
 *
 * A group remains the owner while paused or with its reminder switched off: neither state
 * can silently restart the member's personal reminder. A tracker without any group is
 * eligible for its own configured reminder.
 */
fun coveredIndividualTrackerIds(groups: List<TrackerGroup>): Set<Long> =
    groups.flatMapTo(mutableSetOf()) { it.trackerIds }

/** Shared by alarm registration and alarm delivery to avoid divergent suppression rules. */
fun individualReminderAllowed(tracker: Tracker, coveredByGroup: Boolean): Boolean =
    tracker.reminder && !tracker.paused && !coveredByGroup

/**
 * Assign one stable owner per tracker, using group creation order (database ID).
 *
 * Crucially, paused/disabled groups still participate in ownership. If a member is in two
 * groups, pausing the first must NOT transfer its reminder to the second. Ownership changes
 * only when group membership is explicitly edited or a group is deleted.
 */
fun reminderOwnerGroupByTrackerId(groups: List<TrackerGroup>): Map<Long, Long> {
    val owners = mutableMapOf<Long, Long>()
    groups.sortedBy { it.id }.forEach { group ->
        group.trackerIds.forEach { trackerId -> owners.putIfAbsent(trackerId, group.id) }
    }
    return owners
}

/** For group UI: only scheduled, incomplete members count as due. */
fun missingGroupTrackerCount(group: TrackerGroup, scores: Map<Long, Int>): Int =
    group.trackerIds.count { scores[it] == null }

fun missingGroupTrackerCount(group: TrackerGroup, trackers: List<Tracker>,
                             day: LocalDate, scores: Map<Long, Int>): Int =
    trackers.count { it.id in group.trackerIds && dueAndMissing(it, day, scores) }

/** Stable ownership is decided independently of pause and notification enablement. */
fun reminderOwnerSlotByTrackerId(group: TrackerGroup): Map<Long, String> {
    val owners = mutableMapOf<Long, String>()
    group.effectiveReminderSlots().forEach { slot ->
        slot.trackerIds.filter { it in group.trackerIds }.forEach { id ->
            owners.putIfAbsent(id, slot.id)
        }
    }
    return owners
}

/** Due, incomplete trackers owned by exactly this group AND this reminder slot. */
fun groupNotificationTrackerIds(group: TrackerGroup, slot: GroupReminderSlot,
                                allGroups: List<TrackerGroup>, trackers: List<Tracker>,
                                day: LocalDate, scores: Map<Long, Int>): Set<Long> {
    if (group.paused || !slot.enabled || allGroups.none { it.id == group.id } ||
        group.effectiveReminderSlots().none { it.id == slot.id }) return emptySet()
    val owners = reminderOwnerGroupByTrackerId(allGroups)
    val slotOwners = reminderOwnerSlotByTrackerId(group)
    return trackers.asSequence()
        .filter { owners[it.id] == group.id && slotOwners[it.id] == slot.id }
        .filter { dueAndMissing(it, day, scores) }
        .mapTo(mutableSetOf()) { it.id }
}

/** Backwards-compatible union helper for the group UI. */
fun groupNotificationTrackerIds(group: TrackerGroup, allGroups: List<TrackerGroup>,
                                trackers: List<Tracker>, day: LocalDate,
                                scores: Map<Long, Int>): Set<Long> =
    group.effectiveReminderSlots().flatMapTo(mutableSetOf()) { slot ->
        groupNotificationTrackerIds(group, slot, allGroups, trackers, day, scores)
    }
