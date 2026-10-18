package com.everydayta.app

import java.time.LocalDate

/** Pure logic shared by the notification receiver and its focused check-in activity. */
object QuickEntryRules {
    /** Group membership and reminder-slot ordering remain authoritative, even if paused. */
    fun pending(
        group: TrackerGroup?, slot: GroupReminderSlot?, groups: List<TrackerGroup>,
        trackers: List<Tracker>, today: LocalDate, scores: Map<Long, Int>, individualId: Long = -1L
    ): List<Tracker> {
        if (group != null && slot != null) {
            val eligible = groupNotificationTrackerIds(group, slot, groups, trackers, today, scores)
            return slot.trackerIds.distinct().mapNotNull { id ->
                trackers.firstOrNull { it.id == id && id in eligible }
            }
        }
        if (group != null || slot != null) return emptyList()
        val covered = coveredIndividualTrackerIds(groups)
        return trackers.filter { it.id == individualId &&
            individualReminderAllowed(it, it.id in covered) && dueAndMissing(it, today, scores) }
    }

    /** Replies may be a display score, or an unambiguous configured label. */
    fun parse(tracker: Tracker, input: String): Int? {
        val response = input.trim()
        if (response.isEmpty()) return null
        val index = when (tracker.scaleType) {
            ScaleSemantics.ZERO_TO_TEN -> response.toIntOrNull()?.plus(1)
            else -> {
                val matchingLabels = tracker.labels.withIndex().filter {
                    it.value.equals(response, ignoreCase = true)
                }
                if (matchingLabels.size == 1) matchingLabels.single().index + 1
                else response.toIntOrNull()
            }
        }
        return index?.takeIf { it in 1..tracker.labels.size }
    }

    /** Notification actions use internal indices, so zero-to-ten index 1 displays as 0. */
    fun display(tracker: Tracker, index: Int): String =
        if (tracker.scaleType == ScaleSemantics.ZERO_TO_TEN) (index - 1).toString()
        else tracker.labels[index - 1]
}
