package com.everydayta.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

fun createGroupsTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS tracker_groups(id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, name TEXT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, reminder INTEGER NOT NULL, paused INTEGER NOT NULL DEFAULT 0, reminder_message TEXT NOT NULL DEFAULT '', reminder_slots TEXT NOT NULL DEFAULT '[]', multiple_reminders INTEGER NOT NULL DEFAULT 0)")
    db.execSQL("CREATE TABLE IF NOT EXISTS group_members(group_id INTEGER NOT NULL REFERENCES tracker_groups(id) ON DELETE CASCADE, tracker_id INTEGER NOT NULL REFERENCES trackers(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY(group_id,tracker_id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_group_members_tracker ON group_members(tracker_id)")
}

fun Store.groups(): List<TrackerGroup> {
    val membership = mutableMapOf<Long, MutableList<Long>>()
    readableDatabase.rawQuery("SELECT group_id,tracker_id FROM group_members ORDER BY group_id,position", null).use { c ->
        while (c.moveToNext()) membership.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getLong(1))
    }
    val result = mutableListOf<TrackerGroup>()
    readableDatabase.rawQuery("SELECT id,name,hour,minute,reminder,uuid,paused,reminder_message,reminder_slots,multiple_reminders FROM tracker_groups ORDER BY id", null).use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            result.add(TrackerGroup(id, c.getString(1), c.getInt(2), c.getInt(3),
                c.getInt(4) != 0, membership[id].orEmpty(), c.getString(5), c.getInt(6) != 0, c.getString(7),
                decodeReminderSlots(c.getString(8)), c.getInt(9) != 0))
        }
    }
    return result
}

/** Atomic replacement of membership, preserving group UUID and all tracker/entry data. */
fun Store.saveGroup(group: TrackerGroup): Long {
    require(group.name.isNotBlank() && group.name.length <= 200) { "Name must be 1–200 characters" }
    require(group.hour in 0..23 && group.minute in 0..59) { "Invalid reminder time" }
    require(group.reminderMessage.length <= 240) { "Notification message must be 240 characters or less" }
    require(group.trackerIds.isNotEmpty() && group.trackerIds.distinct().size == group.trackerIds.size) {
        "Select one or more distinct trackers"
    }
    require(runCatching { UUID.fromString(group.uuid) }.isSuccess) { "Invalid group identity" }
    val known = trackers().map { it.id }.toSet()
    require(group.trackerIds.all { it in known }) { "Group includes a missing tracker" }
    validateReminderSlots(group)
    val previous = if (group.id == 0L) null else groups().firstOrNull { it.id == group.id }
    require(group.id == 0L || previous != null) { "Unknown group" }
    require(previous == null || previous.uuid == group.uuid) { "Group identity cannot change" }
    val db = writableDatabase
    val outerTransaction = db.inTransaction()
    if (!outerTransaction) db.beginTransaction()
    try {
        val values = ContentValues().apply {
            put("name", group.name.trim()); put("uuid", group.uuid)
            put("hour", group.hour); put("minute", group.minute)
            put("reminder", if (group.reminder) 1 else 0)
            put("paused", if (group.paused) 1 else 0)
            put("reminder_message", group.reminderMessage.trim())
            put("reminder_slots", encodeReminderSlots(group.reminderSlots))
            put("multiple_reminders", if (group.multipleReminders) 1 else 0)
        }
        val id = if (group.id == 0L) db.insertOrThrow("tracker_groups", null, values)
            else { require(db.update("tracker_groups", values, "id=?", arrayOf(group.id.toString())) == 1); group.id }
        db.delete("group_members", "group_id=?", arrayOf(id.toString()))
        group.trackerIds.forEachIndexed { index, trackerId ->
            db.insertOrThrow("group_members", null, ContentValues().apply {
                put("group_id", id); put("tracker_id", trackerId); put("position", index)
            })
        }
        if (!outerTransaction) db.setTransactionSuccessful()
        return id
    } finally { if (!outerTransaction) db.endTransaction() }
}

fun Store.deleteGroup(id: Long) {
    // ON DELETE CASCADE removes membership, but never the underlying trackers or their entries.
    writableDatabase.delete("tracker_groups", "id=?", arrayOf(id.toString()))
}


/** Validate every slot before writing any group changes. */
fun validateReminderSlots(group: TrackerGroup) {
    require(group.multipleReminders || group.reminderSlots.isEmpty()) {
        "Single reminder groups must not contain reminder slots"
    }
    require(group.reminderSlots.size <= 12) { "A group can have at most 12 reminders" }
    val ids = mutableSetOf<String>()
    val assigned = mutableSetOf<Long>()
    group.reminderSlots.forEach { slot ->
        require(slot.id.isNotBlank() && ids.add(slot.id)) { "Duplicate reminder identity" }
        require(slot.name.isNotBlank() && slot.name.length <= 100) { "Name each reminder (100 characters maximum)" }
        require(slot.hour in 0..23 && slot.minute in 0..59) { "Invalid reminder time" }
        require(slot.message.length <= 240) { "Reminder message is too long" }
        require(slot.trackerIds.isNotEmpty()) { "Every reminder needs at least one tracker" }
        require(slot.trackerIds.distinct().size == slot.trackerIds.size) { "Repeated tracker in reminder" }
        require(slot.trackerIds.all { it in group.trackerIds && assigned.add(it) }) {
            "Each tracker can belong to only one reminder within its group"
        }
    }
}

fun encodeReminderSlots(slots: List<GroupReminderSlot>): String = JSONArray().apply {
    slots.forEach { slot ->
        put(JSONObject().put("id", slot.id).put("name", slot.name)
            .put("hour", slot.hour).put("minute", slot.minute)
            .put("enabled", slot.enabled).put("trackerIds", JSONArray(slot.trackerIds))
            .put("message", slot.message))
    }
}.toString()

fun decodeReminderSlots(raw: String): List<GroupReminderSlot> {
    val items = JSONArray(raw)
    require(items.length() <= 12) { "Too many reminders" }
    return (0 until items.length()).map { index ->
        val o = items.getJSONObject(index)
        val members = o.getJSONArray("trackerIds")
        GroupReminderSlot(o.getString("id"), o.getString("name"), o.getInt("hour"),
            o.getInt("minute"), o.getBoolean("enabled"),
            (0 until members.length()).map { members.getLong(it) }, o.optString("message", ""))
    }
}

/** Prune reminder membership after tracker deletion without disturbing any remaining scores. */
fun Store.pruneGroupSlotsAfterTrackerDeletion() {
    groups().filter { it.multipleReminders }.forEach { group ->
        val retained = group.trackerIds.toSet()
        val slots = group.reminderSlots.map { slot ->
            slot.copy(trackerIds = slot.trackerIds.filter { it in retained })
        }.filter { it.trackerIds.isNotEmpty() }
        if (slots != group.reminderSlots) saveGroup(group.copy(reminderSlots = slots))
    }
}
