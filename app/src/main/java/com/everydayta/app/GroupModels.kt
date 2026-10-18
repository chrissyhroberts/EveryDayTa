package com.everydayta.app

import java.util.UUID

/** Membership is many-to-many; a tracker retains one score regardless of how many groups show it. */
data class TrackerGroup(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val reminder: Boolean,
    val trackerIds: List<Long>,
    val uuid: String = UUID.randomUUID().toString(),
    val paused: Boolean = false,
    val reminderMessage: String = "",
    val reminderSlots: List<GroupReminderSlot> = emptyList(),
    val multipleReminders: Boolean = reminderSlots.isNotEmpty()
)

/** A stable, separately scheduled reminder for a subset of one group's members.
 * A group in single-reminder mode uses its legacy fields; multipleReminders=true means
 * its reminderSlots are authoritative, even when the list is empty after deletions.
 * Membership continues to own the tracker even when every reminder is disabled.
 */
data class GroupReminderSlot(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val trackerIds: List<Long>,
    val message: String = ""
)

fun TrackerGroup.effectiveReminderSlots(): List<GroupReminderSlot> =
    if (multipleReminders) reminderSlots else listOf(
        GroupReminderSlot("legacy", "Check-in", hour, minute, reminder, trackerIds, reminderMessage)
    )
