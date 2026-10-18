package com.everydayta.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteException
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID


data class Tracker(
    val id:Long, val name:String, val labels:List<String>, val palette:Int,
    val hour:Int, val minute:Int, val reminder:Boolean,
    val uuid:String = UUID.randomUUID().toString(),
    val allowPastEntries:Boolean = true,
    val allowFutureEntries:Boolean = false,
    val schedule: RecordingSchedule = RecordingSchedule.DAILY,
    val paused: Boolean = false,
    val reminderMessage: String = "",
    val scaleType: String = ScaleSemantics.inferLegacy(labels)
)
class Store(context:Context):SQLiteOpenHelper(context,"daygrid.db",null,12) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0,schedule TEXT NOT NULL DEFAULT 'daily',paused INTEGER NOT NULL DEFAULT 0,reminder_message TEXT NOT NULL DEFAULT '',scale_type TEXT NOT NULL DEFAULT 'custom')")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_day ON entries(day)")
        createGroupsTables(db)
    }
    override fun onConfigure(db:SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        if(oldVersion < 3) {
            db.execSQL("ALTER TABLE trackers ADD COLUMN uuid TEXT")
            db.rawQuery("SELECT id FROM trackers",null).use { cursor ->
                while(cursor.moveToNext()) db.execSQL("UPDATE trackers SET uuid=? WHERE id=?",arrayOf(UUID.randomUUID().toString(),cursor.getLong(0)))
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tracker_uuid ON trackers(uuid)")
        }
        if (oldVersion < 4) {
            // Existing scoring values are unchanged. Expand the SQLite constraint
            // to accommodate an eleven-point 0–10 scale (stored as scores 1–11).
            db.execSQL("CREATE TABLE entries_v4(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO entries_v4(tracker_id,day,score) SELECT tracker_id,day,score FROM entries")
            db.execSQL("DROP TABLE entries")
            db.execSQL("ALTER TABLE entries_v4 RENAME TO entries")
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_day ON entries(day)")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE trackers ADD COLUMN allow_past INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE trackers ADD COLUMN allow_future INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 7) createGroupsTables(db)
        if (oldVersion < 8) db.execSQL("ALTER TABLE trackers ADD COLUMN schedule TEXT NOT NULL DEFAULT 'daily'")
        if (oldVersion < 9) db.execSQL("ALTER TABLE trackers ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE trackers ADD COLUMN reminder_message TEXT NOT NULL DEFAULT ''")
            // Protect upgrades from the old schema and from already-added fields.
            val columns = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(tracker_groups)", null).use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(1)
            }
            if ("paused" !in columns)
                db.execSQL("ALTER TABLE tracker_groups ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
            if ("reminder_message" !in columns)
                db.execSQL("ALTER TABLE tracker_groups ADD COLUMN reminder_message TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE trackers ADD COLUMN scale_type TEXT NOT NULL DEFAULT 'custom'")
            // Existing eleven-choice 0..10 trackers already store 1..11. Never rewrite entries.
            val zeroToTen = JSONArray(ScaleSemantics.zeroToTenLabels).toString()
            val likert = JSONArray(ScaleSemantics.likertLabels).toString()
            db.execSQL("UPDATE trackers SET scale_type=? WHERE labels=?", arrayOf(ScaleSemantics.ZERO_TO_TEN, zeroToTen))
            db.execSQL("UPDATE trackers SET scale_type=? WHERE labels=?", arrayOf(ScaleSemantics.LIKERT, likert))
        }
        if (oldVersion < 12) {
            // Upgrade-safe even if createGroupsTables() was invoked earlier in this upgrade.
            val columns = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(tracker_groups)", null).use { c ->
                while (c.moveToNext()) columns += c.getString(1)
            }
            if ("reminder_slots" !in columns)
                db.execSQL("ALTER TABLE tracker_groups ADD COLUMN reminder_slots TEXT NOT NULL DEFAULT '[]'")
            if ("multiple_reminders" !in columns)
                db.execSQL("ALTER TABLE tracker_groups ADD COLUMN multiple_reminders INTEGER NOT NULL DEFAULT 0")
        }
    }
    fun trackers():List<Tracker> { val result=mutableListOf<Tracker>(); readableDatabase.rawQuery("SELECT id,name,labels,palette,hour,minute,reminder,uuid,allow_past,allow_future,schedule,paused,reminder_message,scale_type FROM trackers ORDER BY id",null).use { c -> while(c.moveToNext()) { val a=JSONArray(c.getString(2)); result+=Tracker(c.getLong(0),c.getString(1),(0 until a.length()).map { a.getString(it) },c.getInt(3),c.getInt(4),c.getInt(5),c.getInt(6)==1,c.getString(7),c.getInt(8)==1,c.getInt(9)==1,RecordingSchedule.decode(c.getString(10)),c.getInt(11)!=0,c.getString(12),c.getString(13)) } };return result }
    fun save(t:Tracker):Long { require(ScaleSemantics.valid(t.scaleType, t.labels)) { "Scale type and labels do not match" }; require(t.name.isNotBlank() && t.name.length<=200 && t.labels.size in 2..11 && t.labels.all { it.isNotBlank() } && t.palette in Palettes.names.indices && t.hour in 0..23 && t.minute in 0..59); t.schedule.validate(); require(TrackerNameRules.isUnique(t.name, trackers().map { it.id to it.name }, t.id)) { "A tracker with this name already exists" }; require(t.reminderMessage.length<=240) { "Notification message too long" }; val previous=if(t.id!=0L)trackers().firstOrNull{it.id==t.id} else null; require(t.id==0L || previous!=null) { "Unknown tracker" }; require(previous==null || previous.uuid==t.uuid) { "Tracker identity cannot change" }; require(previous==null || (previous.labels==t.labels && previous.scaleType==t.scaleType) || entries(t.id).isEmpty()) { "Changing the number of levels would reinterpret historical entries" }; val v=android.content.ContentValues().apply {put("name",t.name);put("labels",JSONArray(t.labels).toString());put("palette",t.palette);put("hour",t.hour);put("minute",t.minute);put("reminder",if(t.reminder)1 else 0);put("uuid",t.uuid);put("allow_past",if(t.allowPastEntries)1 else 0);put("allow_future",if(t.allowFutureEntries)1 else 0);put("schedule",t.schedule.encode());put("paused",if(t.paused)1 else 0);put("reminder_message",t.reminderMessage.trim());put("scale_type",t.scaleType)}; return if(t.id==0L) writableDatabase.insertOrThrow("trackers",null,v) else {require(writableDatabase.update("trackers",v,"id=?",arrayOf(t.id.toString()))==1);t.id} }
    fun delete(id:Long) { writableDatabase.beginTransaction();try { writableDatabase.delete("entries","tracker_id=?",arrayOf(id.toString()));writableDatabase.delete("trackers","id=?",arrayOf(id.toString()));writableDatabase.delete("tracker_groups", "NOT EXISTS (SELECT 1 FROM group_members WHERE group_members.group_id=tracker_groups.id)", null);pruneGroupSlotsAfterTrackerDeletion();writableDatabase.setTransactionSuccessful() }finally {writableDatabase.endTransaction()} }
    /** Atomic bulk deletion: removes selected trackers, their entries and group memberships.
     * Other group members and their scores remain untouched. Empty groups are removed.
     */
    fun deleteTrackers(ids: Set<Long>): Int {
        require(ids.isNotEmpty()) { "Select trackers to delete" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = trackers().map { it.id }.toSet()
            require(ids.all { it in existing }) { "A selected tracker no longer exists" }
            ids.forEach { id ->
                db.delete("trackers", "id=?", arrayOf(id.toString()))
            }
            db.execSQL("DELETE FROM tracker_groups WHERE NOT EXISTS " +
                "(SELECT 1 FROM group_members WHERE group_members.group_id=tracker_groups.id)")
            pruneGroupSlotsAfterTrackerDeletion()
            db.setTransactionSuccessful()
            return ids.size
        } finally { db.endTransaction() }
    }

    /** One indexed date lookup for the Today screen, regardless of tracker count/history size. */
    fun scoresOn(day: LocalDate): Map<Long, Int> {
        val out = mutableMapOf<Long, Int>()
        readableDatabase.rawQuery("SELECT tracker_id,score FROM entries WHERE day=?", arrayOf(day.toString())).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
        }
        return out
    }
    /** One bounded query for all of a group's observations in the visible month. */
    fun entriesForMonth(trackerIds: List<Long>, month: java.time.YearMonth): Map<Long, Map<LocalDate, Int>> {
        if (trackerIds.isEmpty()) return emptyMap()
        val args = trackerIds.map { it.toString() } + listOf(month.atDay(1).toString(), month.atEndOfMonth().toString())
        val placeholders = trackerIds.joinToString(",") { "?" }
        val result = mutableMapOf<Long, MutableMap<LocalDate, Int>>()
        readableDatabase.rawQuery("SELECT tracker_id,day,score FROM entries " +
            "WHERE tracker_id IN ($placeholders) AND day BETWEEN ? AND ?", args.toTypedArray()).use { c ->
            while (c.moveToNext()) result.getOrPut(c.getLong(0)) { mutableMapOf() }[
                LocalDate.parse(c.getString(1))] = c.getInt(2)
        }
        return result
    }
    fun entries(id:Long):Map<LocalDate,Int> {val out=mutableMapOf<LocalDate,Int>();readableDatabase.rawQuery("SELECT day,score FROM entries WHERE tracker_id=?",arrayOf(id.toString())).use { c -> while(c.moveToNext()) out[LocalDate.parse(c.getString(0))]=c.getInt(1) };return out}
    fun score(id: Long, day: LocalDate, value: Int?, fromImport: Boolean = false) {
        val tracker = trackers().firstOrNull { it.id == id } ?: error("Unknown tracker")
        if (!fromImport) {
            require(!tracker.paused) { "Resume this tracker before recording entries" }
            require(dateEntryAllowed(day, LocalDate.now(), tracker.allowPastEntries, tracker.allowFutureEntries)) {
                "Entry is disabled for this date in tracker settings"
            }
        }
        if (value != null) require(value in 1..tracker.labels.size) { "Score is outside this tracker's scale" }
        if (value == null) writableDatabase.delete("entries", "tracker_id=? AND day=?", arrayOf(id.toString(),day.toString()))
        else writableDatabase.execSQL("INSERT OR REPLACE INTO entries(tracker_id,day,score) VALUES(?,?,?)",arrayOf(id,day.toString(),value))
    }
    fun backup():String {
        val root = JSONObject().put("format", "everydayta-v8")
        val arr = JSONArray()
        val allTrackers = trackers()
        allTrackers.forEach { t ->
            val o=JSONObject().put("name",t.name).put("uuid",t.uuid)
                .put("labels",JSONArray(t.labels)).put("palette",t.palette)
                .put("hour",t.hour).put("minute",t.minute).put("reminder",t.reminder)
                .put("allowPastEntries",t.allowPastEntries).put("allowFutureEntries",t.allowFutureEntries)
                .put("schedule",t.schedule.encode()).put("paused",t.paused)
                .put("reminderMessage",t.reminderMessage).put("scaleType",t.scaleType)
            val e=JSONObject()
            entries(t.id).forEach { (day,score)->e.put(day.toString(),score) }
            o.put("entries",e)
            arr.put(o)
        }
        val uuidById = allTrackers.associate { it.id to it.uuid }
        val groupArr = JSONArray()
        groups().forEach { group ->
            groupArr.put(JSONObject()
                .put("uuid", group.uuid).put("name", group.name)
                .put("hour", group.hour).put("minute", group.minute)
                .put("reminder", group.reminder)
                .put("paused", group.paused).put("reminderMessage", group.reminderMessage)
                .put("multipleReminders", group.multipleReminders)
                .put("reminderSlots", JSONArray().apply {
                    group.reminderSlots.forEach { slot ->
                        put(JSONObject().put("id", slot.id).put("name", slot.name)
                            .put("hour", slot.hour).put("minute", slot.minute)
                            .put("enabled", slot.enabled).put("message", slot.message)
                            .put("trackerUuids", JSONArray(slot.trackerIds.map { uuidById.getValue(it) })))
                    }
                })
                .put("trackerUuids", JSONArray(group.trackerIds.map { uuidById.getValue(it) })))
        }
        return root.put("trackers",arr).put("groups",groupArr).toString(2)
    }
    /** Parse and validate the complete payload before making any destructive changes. */
    fun validateBackup(json:String) {
        require(json.length<=20_000_000)
        val root=JSONObject(json)
        require(root.getString("format") in listOf("daygrid-v1","daygrid-v2","everydayta-v3","everydayta-v4","everydayta-v5","everydayta-v6","everydayta-v7","everydayta-v8"))
        val items=root.getJSONArray("trackers")
        require(items.length()<=500)
        val uuids=mutableSetOf<String>()
        val names=mutableSetOf<String>()
        var count=0
        for(i in 0 until items.length()){
            val o=items.getJSONObject(i);val labels=o.getJSONArray("labels")
            require(o.getString("name").isNotBlank() && o.getString("name").length<=200 && labels.length() in 2..11)
            require(names.add(TrackerNameRules.key(o.getString("name")))) { "Duplicate tracker name in backup" }
            for(j in 0 until labels.length())require(labels.getString(j).isNotBlank())
            if(root.getString("format")!="daygrid-v1") { val uuid=o.getString("uuid"); require(runCatching { UUID.fromString(uuid) }.isSuccess); require(uuids.add(uuid)) { "Duplicate tracker UUID" } }
            require(ScaleSemantics.valid(
                if (o.has("scaleType")) o.getString("scaleType") else ScaleSemantics.inferLegacy(
                    (0 until labels.length()).map { labels.getString(it) }),
                (0 until labels.length()).map { labels.getString(it) })) { "Invalid score-scale metadata" }
            require(o.getInt("palette") in Palettes.names.indices && o.getInt("hour") in 0..23 && o.getInt("minute") in 0..59)
            o.getBoolean("reminder")
            if (o.has("schedule")) RecordingSchedule.decode(o.getString("schedule"))
            require(!o.has("paused") || o.get("paused") is Boolean)
            require(!o.has("reminderMessage") || (o.get("reminderMessage") is String && o.getString("reminderMessage").length<=240))
            require(!o.has("allowPastEntries") || o.get("allowPastEntries") is Boolean)
            require(!o.has("allowFutureEntries") || o.get("allowFutureEntries") is Boolean)
            val entries=o.getJSONObject("entries");count+=entries.length();require(count<=200_000)
            entries.keys().forEach { key->val day=LocalDate.parse(key);require(day.toString()==key && entries.getInt(key) in 1..labels.length()) }
        }
        if (root.getString("format") in listOf("everydayta-v3", "everydayta-v4", "everydayta-v5", "everydayta-v6", "everydayta-v7", "everydayta-v8")) {
            val groupItems = root.getJSONArray("groups")
            require(groupItems.length() <= 500) { "Too many groups" }
            val groupUuids = mutableSetOf<String>()
            for (i in 0 until groupItems.length()) {
                val o = groupItems.getJSONObject(i)
                val uuid = o.getString("uuid")
                require(runCatching { UUID.fromString(uuid) }.isSuccess && groupUuids.add(uuid)) { "Invalid or duplicate group UUID" }
                require(o.getString("name").isNotBlank() && o.getString("name").length <= 200)
                require(o.getInt("hour") in 0..23 && o.getInt("minute") in 0..59)
                o.getBoolean("reminder")
                require(!o.has("paused") || o.get("paused") is Boolean)
                require(!o.has("reminderMessage") || (o.get("reminderMessage") is String && o.getString("reminderMessage").length<=240))
                val members = o.getJSONArray("trackerUuids")
                require(members.length() in 1..500) { "A group must contain at least one tracker" }
                val uniqueMembers = mutableSetOf<String>()
                for (j in 0 until members.length()) {
                    val member = members.getString(j)
                    require(member in uuids && uniqueMembers.add(member)) { "Unknown or repeated tracker in group" }
                }
                if (o.has("multipleReminders"))
                    require(o.get("multipleReminders") is Boolean) { "Invalid multipleReminders value" }
                if (o.has("reminderSlots")) {
                    val slots = o.getJSONArray("reminderSlots")
                    require(slots.length() <= 12) { "Too many group reminders" }
                    val slotIds = mutableSetOf<String>()
                    val assigned = mutableSetOf<String>()
                    for (index in 0 until slots.length()) {
                        val slot = slots.getJSONObject(index)
                        val slotId = slot.getString("id")
                        require(slotId.isNotBlank() && slotIds.add(slotId)) { "Duplicate reminder ID" }
                        require(slot.getString("name").isNotBlank() && slot.getString("name").length <= 100)
                        require(slot.getInt("hour") in 0..23 && slot.getInt("minute") in 0..59)
                        slot.getBoolean("enabled")
                        require(slot.optString("message", "").length <= 240)
                        val slotMembers = slot.getJSONArray("trackerUuids")
                        require(slotMembers.length() > 0) { "Empty reminder" }
                        for (k in 0 until slotMembers.length()) {
                            val member = slotMembers.getString(k)
                            require(member in uniqueMembers && assigned.add(member)) {
                                "Repeated or unknown tracker in reminder schedules"
                            }
                        }
                    }
                }
            }
        }
    }
    fun restore(json:String) {
        validateBackup(json)
        require(json.length <= 20_000_000) { "Backup exceeds 20 MB limit" }
        val root=JSONObject(json)
        require(root.getString("format") in listOf("daygrid-v1","daygrid-v2","everydayta-v3","everydayta-v4","everydayta-v5","everydayta-v6","everydayta-v7","everydayta-v8")) { "Unsupported backup format" }
        val source=root.getJSONArray("trackers")
        require(source.length() <= 500) { "Too many trackers" }
        val parsed=mutableListOf<Pair<Tracker,Map<LocalDate,Int>>>()
        val seenUuids=mutableSetOf<String>()
        val seenNames=mutableSetOf<String>()
        var total=0
        for(i in 0 until source.length()) {
            val o=source.getJSONObject(i)
            val labels=o.getJSONArray("labels")
            val t=Tracker(0,o.getString("name").trim(),(0 until labels.length()).map {labels.getString(it).trim()},o.getInt("palette"),o.getInt("hour"),o.getInt("minute"),o.getBoolean("reminder"),if(o.has("uuid"))o.getString("uuid") else UUID.randomUUID().toString(),
                if(o.has("allowPastEntries"))o.getBoolean("allowPastEntries") else true,
                if(o.has("allowFutureEntries"))o.getBoolean("allowFutureEntries") else false,
                if(o.has("schedule")) RecordingSchedule.decode(o.getString("schedule")) else RecordingSchedule.DAILY,
                if(o.has("paused")) o.getBoolean("paused") else false,
                if(o.has("reminderMessage")) o.getString("reminderMessage") else "",
                if(o.has("scaleType")) o.getString("scaleType") else ScaleSemantics.inferLegacy(
                    (0 until labels.length()).map { labels.getString(it).trim() }))
            require(ScaleSemantics.valid(t.scaleType, t.labels)) { "Invalid scale configuration" }
            require(seenUuids.add(t.uuid)) { "Duplicate tracker UUID" }
            require(seenNames.add(TrackerNameRules.key(t.name))) { "Duplicate tracker name" }
            require(t.name.isNotBlank() && t.name.length<=200 && t.labels.size in 2..11 && t.labels.all{it.isNotBlank()} && t.palette in Palettes.names.indices && t.hour in 0..23 && t.minute in 0..59) { "Invalid tracker at index $i" }
            val e=o.getJSONObject("entries")
            total+=e.length();require(total<=200_000) { "Too many entries" }
            val rows=mutableMapOf<LocalDate,Int>()
            e.keys().forEach { key ->
                val date=LocalDate.parse(key)
                require(date.toString()==key) { "Noncanonical date" }
                val value=e.getInt(key)
                require(value in 1..t.labels.size) { "Invalid score for $key" }
                rows[date]=value
            }
            parsed+=t to rows
        }
        val restoredGroups = if (root.getString("format") in listOf("everydayta-v3", "everydayta-v4", "everydayta-v5", "everydayta-v6", "everydayta-v7", "everydayta-v8")) {
            val array = root.getJSONArray("groups")
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val members = o.getJSONArray("trackerUuids")
                GroupRestoreSpec(o.getString("uuid"), o.getString("name"),
                    o.getInt("hour"), o.getInt("minute"), o.getBoolean("reminder"),
                    (0 until members.length()).map { members.getString(it) },
                    if (o.has("paused")) o.getBoolean("paused") else false,
                    if (o.has("reminderMessage")) o.getString("reminderMessage") else "",
                    if (o.has("reminderSlots")) {
                        val slots = o.getJSONArray("reminderSlots")
                        (0 until slots.length()).map { index ->
                            val slot = slots.getJSONObject(index)
                            val members = slot.getJSONArray("trackerUuids")
                            GroupRestoreSlot(slot.getString("id"), slot.getString("name"),
                                slot.getInt("hour"), slot.getInt("minute"), slot.getBoolean("enabled"),
                                (0 until members.length()).map { members.getString(it) },
                                slot.optString("message", ""))
                        }
                    } else emptyList(),
                    if (o.has("multipleReminders")) o.getBoolean("multipleReminders")
                    else o.has("reminderSlots") && o.getJSONArray("reminderSlots").length() > 0)
            }
        } else emptyList()
        val db=writableDatabase
        db.beginTransaction()
        try {
            // Explicitly remove groups first. Legacy data has no groups.
            db.delete("tracker_groups",null,null)
            db.delete("entries",null,null)
            db.delete("trackers",null,null)
            val restoredIds = mutableMapOf<String, Long>()
            parsed.forEach { (t,rows)->
                val id=save(t)
                restoredIds[t.uuid] = id
                rows.forEach { (date,value)->score(id,date,value,fromImport = true) }
            }
            restoredGroups.forEach { g ->
                saveGroup(TrackerGroup(0, g.name, g.hour, g.minute, g.reminder,
                    g.trackerUuids.map { restoredIds.getValue(it) }, g.uuid, g.paused, g.reminderMessage,
                    g.slots.map { slot -> GroupReminderSlot(slot.id, slot.name,
                        slot.hour, slot.minute, slot.enabled,
                        slot.memberUuids.map { restoredIds.getValue(it) }, slot.message) }, g.multipleReminders))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun normalizeCsv(csv:String):String {
        val records=parseCsv(csv.removePrefix("\uFEFF"))
        val v1 = records.firstOrNull() == listOf("tracker_uuid","tracker","date","score","label")
        val v2 = records.firstOrNull() == listOf("tracker_uuid","tracker","date","score","label","level_index","scale_type")
        if (!v1 && !v2) return csv
        val existing=trackers().associateBy{it.uuid}
        val seen=mutableMapOf<String,String>()
        val names=mutableMapOf<String,String>()
        val output=StringBuilder("tracker,date,score,label\r\n")
        records.drop(1).filter{it.any(String::isNotBlank)}.forEach { row ->
            require(row.size == if (v2) 7 else 5) { "CSV has the wrong number of fields" }
            val uuid=row[0]; require(runCatching {UUID.fromString(uuid)}.isSuccess) {"Invalid tracker UUID"}
            val name=existing[uuid]?.name ?: row[1]
            require(seen.putIfAbsent(uuid,name)==null || seen[uuid]==name)
            require(names.putIfAbsent(name,uuid)==null || names[name]==uuid) { "Conflicting tracker identities for $name" }
            // Never silently attach an unknown UUID to an unrelated tracker sharing its name.
            require(existing.containsKey(uuid) || trackers().none{it.name==name}) { "Tracker name conflicts with another identity: $name" }
            val levelIndex = if (v2) row[5].toIntOrNull() ?: error("Invalid level_index")
                else row[3].toIntOrNull() ?: error("Invalid score")
            require(levelIndex in 1..11) { "Invalid level_index" }
            if (v2) {
                val fromExisting = existing[uuid]
                val type = row[6]
                require(type in listOf(ScaleSemantics.ZERO_TO_TEN,ScaleSemantics.LIKERT,ScaleSemantics.DONE_NOT_DONE,ScaleSemantics.CUSTOM)) {
                    "Invalid scale_type"
                }
                if (fromExisting != null) {
                    require(type == fromExisting.scaleType) { "Scale type mismatch for $name" }
                    require(row[3] == ScaleSemantics.displayScore(fromExisting.scaleType, levelIndex).toString()) {
                        "Score/level_index mismatch for $name"
                    }
                    require(row[4] == fromExisting.labels.getOrNull(levelIndex - 1)) {
                        "Label/level_index mismatch for $name"
                    }
                } else {
                    require(row[3] == ScaleSemantics.displayScore(type, levelIndex).toString()) {
                        "Display score must match level_index and scale_type"
                    }
                    val preset = when(type) {
                        ScaleSemantics.LIKERT -> ScaleSemantics.likertLabels
                        ScaleSemantics.ZERO_TO_TEN -> ScaleSemantics.zeroToTenLabels
                        ScaleSemantics.DONE_NOT_DONE -> ScaleSemantics.doneNotDoneLabels
                        else -> null
                    }
                    if (preset != null) require(row[4] == preset.getOrNull(levelIndex - 1)) {
                        "Label does not match $type scale"
                    }
                }
            }
            output.append(listOf(name,row[2],levelIndex.toString(),row[4]).joinToString(","){ "\""+it.replace("\"","\"\"")+"\"" }).append("\r\n")
        }
        return output.toString()
    }
    fun previewCsv(csv:String):CsvPreview {
        val original=parseCsv(csv.removePrefix("\uFEFF"))
        val presetByName = if (original.firstOrNull() == listOf(
            "tracker_uuid","tracker","date","score","label","level_index","scale_type")) {
            val existingByUuid=trackers().associateBy{it.uuid}
            original.drop(1).filter{it.any(String::isNotBlank)}.groupBy {row ->
                existingByUuid[row[0]]?.name ?: row[1]
            }.mapValues {(_,rows)->
                require(rows.map { it[6] }.distinct().size == 1) { "Conflicting scale types" }
                rows.first()[6]
            }
        } else emptyMap()
        val records=parseCsv(normalizeCsv(csv).removePrefix("\uFEFF"))
        require(records.isNotEmpty() && records.first()==listOf("tracker","date","score","label"))
        require(records.size<=200_001)
        val existing=trackers()
        require(existing.map{it.name}.distinct().size==existing.size){"Ambiguous tracker names"}
        val byName=existing.associateBy{it.name}
        val grouped=mutableMapOf<String,MutableList<Triple<LocalDate,Int,String>>>()
        records.drop(1).filter{it.any(String::isNotBlank)}.forEachIndexed{index,r->
            require(r.size==4){"Row ${index+2}: four fields required"}
            val day=LocalDate.parse(r[1]);require(day.toString()==r[1])
            val score=r[2].toIntOrNull()?:error("Invalid score on row ${index+2}")
            require(r[0].isNotBlank() && r[0].length<=200 && score in 1..11 && r[3].isNotBlank())
            grouped.getOrPut(r[0]){mutableListOf()}.add(Triple(day,score,r[3]))
        }
        var inserted=0;var changed=0;var unchanged=0;var newTrackers=0;var outsideDatePolicy=0
        grouped.forEach{(name,rows)->
            require(rows.map{it.first}.distinct().size==rows.size){"Duplicate dates for $name"}
            val t=byName[name]
            val labels=rows.groupBy{it.second}.mapValues{(_,values)->require(values.map{it.third}.distinct().size==1){"Conflicting labels for $name"};values.first().third}
            if(t==null){
                newTrackers++
                val preset = when (presetByName[name]) {
                    ScaleSemantics.ZERO_TO_TEN -> ScaleSemantics.zeroToTenLabels
                    ScaleSemantics.LIKERT -> ScaleSemantics.likertLabels
                    ScaleSemantics.DONE_NOT_DONE -> ScaleSemantics.doneNotDoneLabels
                    else -> null
                }
                if (preset == null) require(labels.size>=2 && labels.keys==(1..labels.keys.max()).toSet()) {
                    "Incomplete custom scale for $name; use JSON for full backup"
                } else require(labels.all { (index,label) -> preset.getOrNull(index-1)==label }) {
                    "Scale mismatch for $name"
                }
            }
            else require(labels.all{(score,label)->score<=t.labels.size && t.labels[score-1]==label}){"Scale mismatch for $name"}
            val old=if(t!=null)entries(t.id) else emptyMap()
            val allowPast = t?.allowPastEntries ?: true
            val allowFuture = t?.allowFutureEntries ?: false
            outsideDatePolicy += rows.count { (day, _, _) ->
                !dateEntryAllowed(day, LocalDate.now(), allowPast, allowFuture)
            }
            rows.forEach{(day,score,_)->when(old[day]){null->inserted++;score->unchanged++;else->changed++}}
        }
        return CsvPreview(newTrackers,inserted,changed,unchanged,outsideDatePolicy)
    }
    /** CSV is a data-only import: creates trackers using distinct observed labels, or maps to an existing exact name/label scale. */
    fun importCsv(csv:String):Int {
        previewCsv(csv)
        require(csv.length <= 20_000_000) { "CSV exceeds 20 MB" }
        val original = parseCsv(csv.removePrefix("\uFEFF"))
        val records=parseCsv(normalizeCsv(csv).removePrefix("\uFEFF"))
        val uuidByName=if(parseCsv(csv.removePrefix("\uFEFF")).firstOrNull() in listOf(
            listOf("tracker_uuid","tracker","date","score","label"),
            listOf("tracker_uuid","tracker","date","score","label","level_index","scale_type"))) {
            val existingByUuid=trackers().associateBy{it.uuid}
            parseCsv(csv.removePrefix("\uFEFF")).drop(1).filter{it.any(String::isNotBlank)}.associate { row -> (existingByUuid[row[0]]?.name ?: row[1]) to row[0] }
        } else emptyMap()
        val presetByName = if (original.firstOrNull() == listOf(
            "tracker_uuid","tracker","date","score","label","level_index","scale_type")) {
            val existingByUuid=trackers().associateBy { it.uuid }
            original.drop(1).filter{it.any(String::isNotBlank)}.groupBy { row ->
                existingByUuid[row[0]]?.name ?: row[1]
            }.mapValues { (_, rows) ->
                require(rows.map { it[6] }.distinct().size == 1) { "Conflicting scale types" }
                rows.first()[6]
            }
        } else emptyMap()
        require(records.isNotEmpty() && records.first()==listOf("tracker","date","score","label")) { "Expected tracker,date,score,label header" }
        require(records.size<=200_001) { "Too many rows" }
        data class Row(val name:String,val day:LocalDate,val score:Int,val label:String)
        val rows=records.drop(1).filter{it.any(String::isNotBlank)}.mapIndexed { index,r ->
            require(r.size==4) { "CSV row ${index+2}: expected four columns" }
            val day=LocalDate.parse(r[1]);require(day.toString()==r[1]) { "Noncanonical date on row ${index+2}" }
            val score=r[2].toIntOrNull() ?: error("Invalid score on row ${index+2}")
            require(r[0].isNotBlank() && r[0].length<=200 && r[3].isNotBlank() && score in 1..11) { "Invalid row ${index+2}" }
            Row(r[0],day,score,r[3])
        }
        val existing=trackers();require(existing.map{it.name}.distinct().size==existing.size) { "Ambiguous existing tracker names; rename duplicates before CSV import" };val plans=rows.groupBy{it.name}.map { (name, group) ->
            val labels=group.groupBy{it.score}.mapValues{ (_,v)->require(v.map{it.label}.distinct().size==1) {"Conflicting labels for $name"};v.first().label}
            val match=existing.firstOrNull{it.name==name}
            val type = match?.scaleType ?: presetByName[name] ?: ScaleSemantics.CUSTOM
            val scale = if (match != null) match.labels else when (type) {
                ScaleSemantics.ZERO_TO_TEN -> ScaleSemantics.zeroToTenLabels
                ScaleSemantics.LIKERT -> ScaleSemantics.likertLabels
                else -> {
                    require(labels.keys == (1..labels.keys.max()).toSet() && labels.size >= 2) {
                        "New custom tracker '$name' needs all labels from level 1, or a JSON backup"
                    }
                    (1..labels.size).map { labels.getValue(it) }
                }
            }
            require(labels.all{(score,label)->score<=scale.size && scale[score-1]==label}) {"Scale mismatch for $name"}
            require(group.map{it.day}.distinct().size==group.size) {"Duplicate date for $name"}
            Triple(match ?: Tracker(0,name,scale,0,20,0,false,
                uuidByName[name] ?: UUID.randomUUID().toString(), scaleType = type),group,match==null)
        }
        val db=writableDatabase;db.beginTransaction()
        try { plans.forEach { (tracker,group,isNew)->
            val id=if(isNew)save(tracker) else tracker.id
            group.forEach {score(id,it.day,it.score,fromImport = true)}
        };db.setTransactionSuccessful() } finally {db.endTransaction()}
        return rows.size
    }
    /** RFC 4180-style parser: quoted fields, escaped quotes and embedded CRLF. */
    private fun parseCsv(input:String):List<List<String>> {
        val out=mutableListOf<List<String>>()
        var row=mutableListOf<String>()
        val field=StringBuilder()
        var quoted=false
        var closedQuote=false
        var i=0
        while(i<input.length) {
            val c=input[i]
            if(quoted) {
                if(c=='"' && i+1<input.length && input[i+1]=='"') {field.append('"');i++}
                else if(c=='"') {quoted=false;closedQuote=true}
                else field.append(c)
            } else when(c) {
                '"' -> {require(field.isEmpty() && !closedQuote) {"Unexpected quote"};quoted=true}
                ',' -> {row.add(field.toString());field.clear();closedQuote=false}
                '\r','\n' -> {row.add(field.toString());field.clear();out.add(row);row=mutableListOf();closedQuote=false;if(c=='\r' && i+1<input.length && input[i+1]=='\n')i++}
                else -> {require(!closedQuote) {"Unexpected character after closing quote"};field.append(c)}
            }
            i++
        }
        require(!quoted) {"Unclosed quoted field"}
        if(row.isNotEmpty() || field.isNotEmpty() || closedQuote) {row.add(field.toString());out.add(row)}
        return out
    }
    fun csv():String {
        val b=StringBuilder("tracker_uuid,tracker,date,score,label,level_index,scale_type\r\n")
        trackers().forEach { t ->
            entries(t.id).toSortedMap().forEach { (date, level) ->
                val values=listOf(t.uuid,t.name,date.toString(),
                    ScaleSemantics.displayScore(t.scaleType,level).toString(),
                    t.labels[level-1],level.toString(),t.scaleType)
                b.append(values.joinToString(",") { "\""+it.replace("\"","\"\"")+"\"" }).append("\r\n")
            }
        }
        return b.toString()
    }
}

private data class GroupRestoreSpec(
    val uuid: String, val name: String, val hour: Int, val minute: Int,
    val reminder: Boolean, val trackerUuids: List<String>,
    val paused: Boolean, val reminderMessage: String,
    val slots: List<GroupRestoreSlot>, val multipleReminders: Boolean
)

private data class GroupRestoreSlot(
    val id: String, val name: String, val hour: Int, val minute: Int,
    val enabled: Boolean, val memberUuids: List<String>, val message: String
)

data class CsvPreview(
    val newTrackers: Int, val inserted: Int, val changed: Int, val unchanged: Int,
    val outsideDatePolicy: Int = 0
)
