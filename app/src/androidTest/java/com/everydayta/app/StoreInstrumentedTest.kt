package com.everydayta.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: Store
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        context.deleteDatabase("daygrid.db")
        store=Store(context)
    }
    @After fun teardown() { store.close();context.deleteDatabase("daygrid.db") }
    @Test fun duplicateTrackerNamesAreRejectedButRenamesAreAllowed() {
        val first = store.save(Tracker(0,"Pain",listOf("Low","High"),0,20,0,false))
        val old = store.trackers().single()
        try { store.save(Tracker(0," pain ",listOf("No","Yes"),0,20,0,false));fail("Duplicate name") }
        catch (_:IllegalArgumentException) {}
        store.save(old.copy(name="PAIN"))
        assertEquals(first,store.trackers().single().id)
    }
    @Test fun bulkDeleteRemovesOnlySelectedTrackerAndGroupMembership() {
        val one=store.save(Tracker(0,"One",listOf("No","Yes"),0,20,0,false))
        val two=store.save(Tracker(0,"Two",listOf("No","Yes"),0,20,0,false))
        val three=store.save(Tracker(0,"Three",listOf("No","Yes"),0,20,0,false))
        store.score(one,LocalDate.of(2026,10,18),2)
        store.score(two,LocalDate.of(2026,10,18),1)
        store.score(three,LocalDate.of(2026,10,18),2)
        store.saveGroup(TrackerGroup(0,"Routine",9,30,true,listOf(one,two,three)))
        assertEquals(2,store.deleteTrackers(setOf(one,two)))
        assertEquals(listOf("Three"),store.trackers().map { it.name })
        assertEquals(listOf(three),store.groups().single().trackerIds)
        assertEquals(2,store.entries(three)[LocalDate.of(2026,10,18)])
    }
    @Test fun versionedZeroToTenCsvUsesVisibleNumbersAndImportsCorrectIndices() {
        val id=store.save(Tracker(0,"Pain",(0..10).map(Int::toString),2,20,0,false))
        val first=LocalDate.of(2026,10,17)
        val last=first.plusDays(1)
        store.score(id,first,1)
        store.score(id,last,11)
        val csv=store.csv()
        assertTrue(csv.startsWith("tracker_uuid,tracker,date,score,label,level_index,scale_type"))
        assertTrue(csv.contains("\"0\",\"0\",\"1\",\"zero_to_ten\""))
        assertTrue(csv.contains("\"10\",\"10\",\"11\",\"zero_to_ten\""))
        store.deleteTrackers(setOf(id))
        assertEquals(2,store.importCsv(csv))
        val restored=store.trackers().single()
        assertEquals(ScaleSemantics.ZERO_TO_TEN,restored.scaleType)
        assertEquals(1,store.entries(restored.id)[first])
        assertEquals(11,store.entries(restored.id)[last])
    }
    @Test fun jsonBackupRetainsScaleType() {
        store.save(Tracker(0,"Mood",ScaleSemantics.likertLabels,0,20,0,false))
        val json=store.backup()
        assertTrue(json.contains("\"scaleType\""))
        store.restore(json)
        assertEquals(ScaleSemantics.LIKERT,store.trackers().single().scaleType)
    }
    @Test fun backupRestorePreservesUuidAndScores() {
        val id=store.save(Tracker(0,"Energy",listOf("Low","High"),0,20,0,false))
        val date=LocalDate.of(2026,10,17)
        store.score(id,date,2)
        val original=store.trackers().single().uuid
        val json=store.backup()
        store.restore(json)
        val restored=store.trackers().single()
        assertEquals(original,restored.uuid)
        assertEquals(2,store.entries(restored.id)[date])
    }
    @Test fun malformedBackupDoesNotDestroyData() {
        store.save(Tracker(0,"Keep",listOf("No","Yes"),0,20,0,false))
        try {store.restore("{\"format\":\"daygrid-v2\",\"trackers\":[{}]}");fail("Expected validation failure")}
        catch (_:Exception) {}
        assertEquals("Keep",store.trackers().single().name)
    }
    @Test fun csvPreviewAndImport() {
        store.save(Tracker(0,"Mood",listOf("Low","High"),0,20,0,false))
        val csv="tracker,date,score,label\r\n\"Mood\",\"2026-10-17\",\"2\",\"High\"\r\n"
        assertEquals(1,store.previewCsv(csv).inserted)
        assertEquals(1,store.importCsv(csv))
        assertEquals(1,store.previewCsv(csv).unchanged)
    }
    @Test fun duplicateUuidBackupRejected() {
        val id=store.save(Tracker(0,"A",listOf("Low","High"),0,20,0,false))
        val obj=org.json.JSONObject(store.backup())
        val arr=obj.getJSONArray("trackers")
        arr.put(org.json.JSONObject(arr.getJSONObject(0).toString()).put("name","B"))
        try {store.restore(obj.toString());fail("Expected duplicate UUID rejection")}
        catch (_:Exception) {}
        assertEquals(id,store.trackers().single().id)
    }
    @Test fun editingTrackerKeepsStableIdentity() {
        val id=store.save(Tracker(0,"Energy",listOf("Low","High"),0,20,0,false))
        val original=store.trackers().single()
        store.save(original.copy(name="Energy revised"))
        assertEquals(original.uuid,store.trackers().single().uuid)
        try {store.save(original.copy(uuid=java.util.UUID.randomUUID().toString()));fail("Expected immutable identity")}
        catch (_:IllegalArgumentException) {}
    }
    @Test fun duplicateUuidFailsAtValidationStage() {
        store.save(Tracker(0,"A",listOf("Low","High"),0,20,0,false))
        val json=org.json.JSONObject(store.backup())
        json.getJSONArray("trackers").put(org.json.JSONObject(json.getJSONArray("trackers").getJSONObject(0).toString()))
        try {store.validateBackup(json.toString());fail("Expected duplicate identity")}
        catch (_:IllegalArgumentException) {}
    }
    @Test fun csvUuidSurvivesNewTrackerImport() {
        val uuid=java.util.UUID.randomUUID().toString()
        val csv="tracker_uuid,tracker,date,score,label\r\n\"$uuid\",\"Sleep\",\"2026-10-16\",\"1\",\"Poor\"\r\n\"$uuid\",\"Sleep\",\"2026-10-17\",\"2\",\"Good\"\r\n"
        assertEquals(2,store.importCsv(csv))
        assertEquals(uuid,store.trackers().single().uuid)
    }

    @Test fun zeroToTenScaleSavesHighestScoreAndRestores() {
        val id=store.save(Tracker(0,"Pain",(0..10).map { it.toString() },2,20,0,false))
        val day=LocalDate.of(2026,10,18)
        store.score(id,day,11) // internal score 11 represents displayed label "10"
        assertEquals(11,store.entries(id)[day])
        val exported=store.csv()
        assertTrue(exported.contains("\"10\",\"10\",\"11\",\"zero_to_ten\""))
        store.restore(store.backup())
        val restored=store.trackers().single()
        assertEquals(11,restored.labels.size)
        assertEquals(11,store.entries(restored.id)[day])
    }

    @Test fun todayScoresReturnsOnlyRequestedDayAcrossManyTrackers() {
        val chosen = LocalDate.of(2026, 10, 18)
        val yesterday = chosen.minusDays(1)
        for (i in 1..25) {
            val id = store.save(Tracker(0, "Tracker $i", listOf("Low", "High"), 0, 20, 0, false))
            store.score(id, yesterday, 1)
            if (i % 2 == 0) store.score(id, chosen, 2)
        }
        val entries = store.scoresOn(chosen)
        assertEquals(12, entries.size)
        assertTrue(entries.values.all { it == 2 })
    }

    @Test fun upgradeFromV4PreservesScoresAndIndexesDayLookup() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db = context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO trackers VALUES(1,'Mood','[\"Low\",\"High\"]',0,20,0,0,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO entries(tracker_id,day,score) VALUES(1,'2026-10-18',2)")
        db.version = 4
        db.close()
        store = Store(context)
        assertEquals(2, store.scoresOn(LocalDate.of(2026,10,18))[1L])
        val indexes = mutableListOf<String>()
        store.readableDatabase.rawQuery("PRAGMA index_list('entries')", null).use { c ->
            while (c.moveToNext()) indexes.add(c.getString(1))
        }
        assertTrue(indexes.contains("idx_entries_day"))
    }

    @Test fun upgradeFromV3KeepsPriorEntries() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db=context.openOrCreateDatabase("daygrid.db",Context.MODE_PRIVATE,null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 10),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO trackers(id,name,labels,palette,hour,minute,reminder,uuid) VALUES(1,'Mood','[\"Bad\",\"Good\"]',0,20,0,0,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO entries(tracker_id,day,score) VALUES(1,'2026-10-17',2)")
        db.version=3
        db.close()
        store=Store(context)
        assertEquals(2,store.entries(1L)[LocalDate.of(2026,10,17)])
        val id=store.save(Tracker(0,"Pain",(0..10).map { it.toString() },1,20,0,false))
        store.score(id,LocalDate.of(2026,10,18),11)
        assertEquals(11,store.entries(id)[LocalDate.of(2026,10,18)])
    }
    @Test fun clearingTodayLeavesOlderScoresAlone() {
        val tracker = Tracker(0, "Energy", listOf("Low", "High"), 0, 20, 0, false)
        val id = store.save(tracker)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        store.score(id, yesterday, 1)
        store.score(id, today, 2)
        assertEquals(2, store.scoresOn(today)[id])
        store.score(id, today, null)
        assertNull(store.scoresOn(today)[id])
        assertEquals(1, store.entries(id)[yesterday])
    }

    @Test fun dateEntryPermissionsAreEnforcedAndBackedUp() {
        val tracker = Tracker(0, "Planning", listOf("No", "Yes"), 0, 20, 0, false,
            allowPastEntries = false, allowFutureEntries = true)
        val id = store.save(tracker)
        val today = LocalDate.now()
        try { store.score(id, today.minusDays(1), 1); fail("Past should be locked") }
        catch (_: IllegalArgumentException) { }
        store.score(id, today.plusDays(1), 2)
        store.restore(store.backup())
        val restored = store.trackers().single()
        assertFalse(restored.allowPastEntries)
        assertTrue(restored.allowFutureEntries)
        assertEquals(2, store.entries(restored.id)[today.plusDays(1)])
    }

    @Test fun restoringOlderBackupDefaultsToPastAllowedAndFutureLocked() {
        store.save(Tracker(0, "Original", listOf("No", "Yes"), 0, 20, 0, false,
            allowPastEntries = false, allowFutureEntries = true))
        val root = org.json.JSONObject(store.backup())
        val tracker = root.getJSONArray("trackers").getJSONObject(0)
        tracker.remove("allowPastEntries")
        tracker.remove("allowFutureEntries")
        store.validateBackup(root.toString())
        store.restore(root.toString())
        assertTrue(store.trackers().single().allowPastEntries)
        assertFalse(store.trackers().single().allowFutureEntries)
    }

    @Test fun csvImportCanRestoreScoresOutsideManualDateWindow() {
        val id = store.save(Tracker(0, "Mood", listOf("Low", "High"), 0, 20, 0, false,
            allowPastEntries = false, allowFutureEntries = false))
        val past = LocalDate.now().minusDays(4)
        val csv = "tracker,date,score,label\r\n\"Mood\",\"$past\",\"2\",\"High\"\r\n"
        assertEquals(1, store.previewCsv(csv).outsideDatePolicy)
        assertEquals(1, store.importCsv(csv))
        assertEquals(2, store.entries(id)[past])
    }

    @Test fun upgradeV5ToV6PreservesDataAndDateDefaults() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db = context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO trackers VALUES(1,'K-P','[\"0\",\"1\",\"2\"]',2,20,0,0,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO entries VALUES(1,'2026-10-18',3)")
        db.version=5
        db.close()
        store = Store(context)
        val tracker=store.trackers().single()
        assertEquals("11111111-1111-4111-8111-111111111111",tracker.uuid)
        assertTrue(tracker.allowPastEntries)
        assertFalse(tracker.allowFutureEntries)
        assertEquals(3,store.entries(tracker.id)[LocalDate.of(2026,10,18)])
    }

    @Test fun groupMembershipAndScoresRemainShared() {
        val mood = store.save(Tracker(0,"Mood",listOf("Low","High"),0,20,0,true))
        val sleep = store.save(Tracker(0,"Sleep",listOf("Poor","Good"),1,20,0,true))
        val groupId = store.saveGroup(TrackerGroup(0,"Evening",21,15,true,listOf(mood,sleep)))
        val group = store.groups().single()
        assertEquals(groupId,group.id)
        assertEquals(listOf(mood,sleep),group.trackerIds)
        assertEquals(setOf(mood,sleep),coveredIndividualTrackerIds(store.groups()))
        val today=LocalDate.now()
        assertEquals(2,missingGroupTrackerCount(group,store.scoresOn(today)))
        store.score(mood,today,2)
        assertEquals(1,missingGroupTrackerCount(group,store.scoresOn(today)))
        store.score(sleep,today,1)
        assertEquals(0,missingGroupTrackerCount(group,store.scoresOn(today)))
        store.deleteGroup(groupId)
        assertEquals(2,store.trackers().size)
        assertEquals(2,store.scoresOn(today).size)
    }

    @Test fun groupsSurviveBackupAndRestoredNumericIds() {
        val a=store.save(Tracker(0,"A",listOf("No","Yes"),0,20,0,false))
        val b=store.save(Tracker(0,"B",listOf("Low","High"),1,20,0,false))
        store.saveGroup(TrackerGroup(0,"Morning",7,45,true,listOf(a,b)))
        val previousGroup = store.groups().single()
        val ids = store.trackers().associate { it.id to it.uuid }
        val content = store.backup()
        assertTrue(content.contains("everydayta-v3"))
        store.restore(content)
        val restored = store.groups().single()
        assertEquals(previousGroup.uuid, restored.uuid)
        assertEquals(7, restored.hour)
        assertEquals(45, restored.minute)
        assertEquals(ids.values.toSet(), restored.trackerIds.map { memberId ->
            store.trackers().first { it.id == memberId }.uuid
        }.toSet())
    }

    @Test fun invalidGroupBackupDoesNotOverwriteExistingData() {
        val id=store.save(Tracker(0,"Keep",listOf("No","Yes"),0,20,0,false))
        store.saveGroup(TrackerGroup(0,"Keep Group",20,0,true,listOf(id)))
        val obj=org.json.JSONObject(store.backup())
        obj.getJSONArray("groups").getJSONObject(0)
            .getJSONArray("trackerUuids").put("11111111-1111-4111-8111-111111111111")
        try { store.restore(obj.toString()); fail("Should reject unknown group member") }
        catch (_: IllegalArgumentException) { }
        assertEquals("Keep",store.trackers().single().name)
        assertEquals("Keep Group",store.groups().single().name)
    }

    @Test fun oldV2BackupRestoresWithoutGroups() {
        val id=store.save(Tracker(0,"Mood",listOf("Low","High"),0,20,0,false))
        store.saveGroup(TrackerGroup(0,"Evening",20,0,true,listOf(id)))
        val old=org.json.JSONObject(store.backup())
        old.put("format","daygrid-v2")
        old.remove("groups")
        store.restore(old.toString())
        assertTrue(store.groups().isEmpty())
        assertEquals("Mood",store.trackers().single().name)
    }

    @Test fun deletingTrackerCleansMembershipAndEmptyGroups() {
        val a=store.save(Tracker(0,"A",listOf("No","Yes"),0,20,0,false))
        val b=store.save(Tracker(0,"B",listOf("No","Yes"),0,20,0,false))
        store.saveGroup(TrackerGroup(0,"Daily",20,0,true,listOf(a,b)))
        store.delete(a)
        assertEquals(listOf(b),store.groups().single().trackerIds)
        store.delete(b)
        assertTrue(store.groups().isEmpty())
    }

    @Test fun upgradeV6CreatesGroupsWithoutChangingEntries() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db=context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE,null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO trackers VALUES(1,'Energy','[\"Low\",\"High\"]',0,20,0,0,'11111111-1111-4111-8111-111111111111',1,0)")
        db.execSQL("INSERT INTO entries VALUES(1,'2026-10-18',2)")
        db.version=6
        db.close()
        store=Store(context)
        assertTrue(store.groups().isEmpty())
        assertEquals(2,store.entries(1L)[LocalDate.of(2026,10,18)])
        val newId=store.saveGroup(TrackerGroup(0,"Night",22,0,true,listOf(1L)))
        assertEquals(newId,store.groups().single().id)
    }

    @Test fun scheduleSurvivesBackupAndDoesNotEraseEntries() {
        val rule=RecordingSchedule(RecordingSchedule.Kind.EVERY_N_WEEKS,3,
            LocalDate.of(2026,10,5), setOf(3,6))
        val id=store.save(Tracker(0,"Exercise",listOf("Skipped","Done"),0,8,0,true,schedule=rule))
        store.score(id,LocalDate.of(2026,10,17),2)
        val json=store.backup()
        assertEquals("everydayta-v7", org.json.JSONObject(json).getString("format"))
        store.restore(json)
        assertEquals(rule,store.trackers().single().schedule)
        assertEquals(2,store.entries(store.trackers().single().id)[LocalDate.of(2026,10,17)])
    }

    @Test fun invalidScheduleBackupLeavesExistingDataAlone() {
        val id=store.save(Tracker(0,"Preserve",listOf("No","Yes"),0,20,0,false))
        val json=org.json.JSONObject(store.backup())
        json.getJSONArray("trackers").getJSONObject(0).put("schedule","r1|EVERY_N_DAYS|0|2026-10-18|1|1|1|1")
        try { store.restore(json.toString()); fail("Expected invalid schedule rejection") }
        catch (_: IllegalArgumentException) {}
        assertEquals(id,store.trackers().single().id)
    }

    @Test fun upgradeFromV7CreatesScheduleAndPreservesGroupMembership() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db=context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE,null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX idx_entries_day ON entries(day)")
        createGroupsTables(db)
        db.execSQL("INSERT INTO trackers VALUES(1,'Energy','[\"Low\",\"High\"]',0,20,0,0,'11111111-1111-4111-8111-111111111111',1,0)")
        db.execSQL("INSERT INTO entries VALUES(1,'2026-10-18',2)")
        db.execSQL("INSERT INTO tracker_groups(id,uuid,name,hour,minute,reminder) VALUES(1,'22222222-2222-4222-8222-222222222222','AM',8,0,1)")
        db.execSQL("INSERT INTO group_members VALUES(1,1,0)")
        db.version=7
        db.close()
        store=Store(context)
        assertEquals(RecordingSchedule.DAILY,store.trackers().single().schedule)
        assertEquals(2,store.entries(1L)[LocalDate.of(2026,10,18)])
        assertEquals(listOf(1L),store.groups().single().trackerIds)
    }

    @Test fun pausePreservesEntriesAndPersistsThroughBackupRestore() {
        val t = Tracker(0, "Focus", listOf("No", "Yes"), 2, 20, 0, true)
        val id = store.save(t)
        val day = LocalDate.now()
        store.score(id, day, 2)
        store.save(store.trackers().single().copy(paused = true))
        assertTrue(store.trackers().single().paused)
        assertEquals(2, store.entries(id)[day])
        try { store.score(id, day, 1); fail("Paused tracker accepted a score") }
        catch (_: IllegalArgumentException) {}
        val snapshot = store.backup()
        assertEquals("everydayta-v7", org.json.JSONObject(snapshot).getString("format"))
        store.restore(snapshot)
        assertTrue(store.trackers().single().paused)
        assertEquals(2, store.entries(store.trackers().single().id)[day])
        store.save(store.trackers().single().copy(paused = false))
        assertFalse(store.trackers().single().paused)
        store.score(store.trackers().single().id, day, 1)
        assertEquals(1, store.entries(store.trackers().single().id)[day])
    }

    @Test fun upgradeFromV8AddsPauseFlagWithoutChangingHistoryOrGroups() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db = context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0,schedule TEXT NOT NULL DEFAULT 'daily')")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO trackers(id,name,labels,palette,hour,minute,reminder,uuid) VALUES(1,'Mood','[\"Low\",\"High\"]',0,8,0,1,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO entries VALUES(1,'2026-10-18',2)")
        createGroupsTables(db)
        db.execSQL("INSERT INTO tracker_groups(id,uuid,name,hour,minute,reminder) VALUES(1,'22222222-2222-4222-8222-222222222222','AM',8,0,1)")
        db.execSQL("INSERT INTO group_members VALUES(1,1,0)")
        db.version = 8
        db.close()
        store = Store(context)
        assertFalse(store.trackers().single().paused)
        assertEquals(2, store.entries(1L)[LocalDate.of(2026,10,18)])
        assertEquals(listOf(1L), store.groups().single().trackerIds)
    }

    @Test fun oldBackupWithoutPauseFieldDefaultsToActive() {
        val id = store.save(Tracker(0, "Keep", listOf("No", "Yes"), 1, 9, 0, false, paused = true))
        val snapshot = org.json.JSONObject(store.backup())
        snapshot.put("format", "everydayta-v4")
        snapshot.getJSONArray("trackers").getJSONObject(0).remove("paused")
        store.restore(snapshot.toString())
        assertFalse(store.trackers().single().paused)
    }

    @Test fun groupPauseAndCustomMessagesSurviveRestore() {
        val id = store.save(Tracker(0, "Study", listOf("No", "Yes"), 0, 18, 30, true,
            reminderMessage = "How did studying go?"))
        val gid = store.saveGroup(TrackerGroup(0, "Evening", 20, 0, true, listOf(id),
            paused = true, reminderMessage = "Time to check in before bed"))
        val day = LocalDate.now()
        store.score(id, day, 2)
        assertEquals(setOf(id), coveredIndividualTrackerIds(store.groups()))
        val json = store.backup()
        assertEquals("everydayta-v7", org.json.JSONObject(json).getString("format"))
        store.restore(json)
        val restoredTracker = store.trackers().single()
        val restoredGroup = store.groups().single()
        assertEquals("How did studying go?", restoredTracker.reminderMessage)
        assertEquals("Time to check in before bed", restoredGroup.reminderMessage)
        assertTrue(restoredGroup.paused)
        assertEquals(listOf(restoredTracker.id), restoredGroup.trackerIds)
        assertEquals(2, store.entries(restoredTracker.id)[day])
        assertEquals(setOf(restoredTracker.id), coveredIndividualTrackerIds(store.groups()))
        store.saveGroup(restoredGroup.copy(paused = false))
        assertFalse(store.groups().single().paused)
        assertEquals(setOf(restoredTracker.id), coveredIndividualTrackerIds(store.groups()))
    }

    @Test fun groupMembershipControlsReminderOwnershipAcrossPauseAndDeletion() {
        val a = store.save(Tracker(0, "A", listOf("No", "Yes"), 0, 8, 0, true))
        val b = store.save(Tracker(0, "B", listOf("No", "Yes"), 0, 8, 0, true))
        val earlyId = store.saveGroup(TrackerGroup(0, "Early", 8, 0, true, listOf(a, b)))
        val lateId = store.saveGroup(TrackerGroup(0, "Late", 20, 0, true, listOf(b)))
        val groups = store.groups()
        val early = groups.single { it.id == earlyId }
        val late = groups.single { it.id == lateId }
        val day = LocalDate.now()
        assertEquals(setOf(a, b), coveredIndividualTrackerIds(groups))
        assertEquals(setOf(a, b), groupNotificationTrackerIds(early, groups, store.trackers(), day, emptyMap()))
        assertTrue(groupNotificationTrackerIds(late, groups, store.trackers(), day, emptyMap()).isEmpty())

        store.saveGroup(early.copy(paused = true))
        val paused = store.groups()
        assertEquals(setOf(a, b), coveredIndividualTrackerIds(paused))
        assertTrue(groupNotificationTrackerIds(paused.single { it.id == earlyId }, paused,
            store.trackers(), day, emptyMap()).isEmpty())
        assertTrue(groupNotificationTrackerIds(paused.single { it.id == lateId }, paused,
            store.trackers(), day, emptyMap()).isEmpty()) // no transfer to overlapping group

        store.saveGroup(paused.single { it.id == earlyId }.copy(reminder = false))
        assertEquals(setOf(a, b), coveredIndividualTrackerIds(store.groups()))
        store.deleteGroup(earlyId)
        assertEquals(setOf(b), coveredIndividualTrackerIds(store.groups()))
        assertEquals(lateId, reminderOwnerGroupByTrackerId(store.groups())[b])
        store.deleteGroup(lateId)
        assertTrue(coveredIndividualTrackerIds(store.groups()).isEmpty())
        assertEquals(2, store.trackers().size) // never deletes member observations or trackers
    }

    @Test fun shareCsvContainsOnlySelectedYearAndTracker() {
        val a = store.save(Tracker(0, "Mood", listOf("Bad", "Good"), 0, 8, 0, false))
        val b = store.save(Tracker(0, "Private", listOf("Low", "High"), 1, 8, 0, false))
        store.score(a, LocalDate.of(2026, 10, 18), 2)
        store.score(a, LocalDate.of(2025, 10, 18), 1)
        store.score(b, LocalDate.of(2026, 10, 18), 1)
        val tracker = store.trackers().single { it.id == a }
        val uri = ShareExports.dataset(context, tracker, store.entries(a), 2026)
        val csv = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertTrue(csv.contains("\"2026-10-18\""))
        assertFalse(csv.contains("2025-10-18"))
        assertFalse(csv.contains("Private"))
        assertTrue(csv.contains("tracker_uuid,tracker,date,score,label"))
    }

    @Test fun corruptedV6GroupSettingsDoNotDeleteExistingData() {
        val id = store.save(Tracker(0, "Keep", listOf("Low", "High"), 0, 8, 0, false))
        store.saveGroup(TrackerGroup(0, "Group", 9, 0, true, listOf(id)))
        val json = org.json.JSONObject(store.backup())
        json.getJSONArray("groups").getJSONObject(0).put("reminderMessage", "x".repeat(241))
        try { store.restore(json.toString()); fail("Expected failure") }
        catch (_: IllegalArgumentException) { }
        assertEquals("Keep", store.trackers().single().name)
        assertEquals("Group", store.groups().single().name)
    }

    @Test fun upgradingFromV9PreservesGroupAndAddsMessageAndPauseColumns() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db = context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0,schedule TEXT NOT NULL DEFAULT 'daily',paused INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER, day TEXT, score INTEGER, PRIMARY KEY(tracker_id,day))")
        db.execSQL("CREATE TABLE tracker_groups(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT UNIQUE,name TEXT,hour INTEGER,minute INTEGER,reminder INTEGER)")
        db.execSQL("CREATE TABLE group_members(group_id INTEGER, tracker_id INTEGER,position INTEGER, PRIMARY KEY(group_id,tracker_id))")
        db.execSQL("INSERT INTO trackers(id,name,labels,palette,hour,minute,reminder,uuid) VALUES(1,'Study','[\"No\",\"Yes\"]',0,20,0,1,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO entries VALUES(1,'2026-10-18',2)")
        db.execSQL("INSERT INTO tracker_groups(id,uuid,name,hour,minute,reminder) VALUES(1,'22222222-2222-4222-8222-222222222222','Evening',20,0,1)")
        db.execSQL("INSERT INTO group_members VALUES(1,1,0)")
        db.version = 9
        db.close()
        store = Store(context)
        assertEquals("", store.trackers().single().reminderMessage)
        assertEquals("", store.groups().single().reminderMessage)
        assertFalse(store.groups().single().paused)
        assertEquals(2,store.entries(1L)[LocalDate.of(2026,10,18)])
        store.saveGroup(store.groups().single().copy(paused=true,reminderMessage="See you tomorrow"))
        assertTrue(store.groups().single().paused)
        assertEquals("See you tomorrow",store.groups().single().reminderMessage)
    }

    @Test fun sharedCalendarProducesPng() {
        val id=store.save(Tracker(0,"Mood",listOf("Low","High"),1,8,0,false))
        val t=store.trackers().single()
        store.score(id,LocalDate.of(2026,10,18),2)
        val uri=ShareExports.image(context,t,store.entries(id),2026,2,0,true)
        val bytes=context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(bytes.size>1000)
        assertEquals(0x89,bytes[0].toInt() and 0xff)
        assertEquals('P'.code,bytes[1].toInt() and 0xff)
        assertEquals('N'.code,bytes[2].toInt() and 0xff)
        assertEquals('G'.code,bytes[3].toInt() and 0xff)
    }


    @Test fun binaryCsvAndBackupPreserveExplicitNotDoneAndDone() {
        val id = store.save(Tracker(0, "Gabby AM", ScaleSemantics.doneNotDoneLabels,
            4, 8, 0, false, scaleType = ScaleSemantics.DONE_NOT_DONE))
        val first = LocalDate.of(2026, 10, 17)
        store.score(id, first, 1)
        store.score(id, first.plusDays(1), 2)
        val csv = store.csv()
        assertTrue(csv.contains("\"Not done\",\"1\",\"done_not_done\""))
        assertTrue(csv.contains("\"Done\",\"2\",\"done_not_done\""))
        val backup = store.backup()
        store.restore(backup)
        val tracker = store.trackers().single()
        assertEquals(ScaleSemantics.DONE_NOT_DONE, tracker.scaleType)
        assertEquals(1, store.entries(tracker.id)[first])
        assertEquals(2, store.entries(tracker.id)[first.plusDays(1)])
        store.delete(tracker.id)
        assertEquals(2, store.importCsv(csv))
        assertEquals(ScaleSemantics.DONE_NOT_DONE, store.trackers().single().scaleType)
    }

    @Test fun multipleGroupRemindersSurviveBackupRestoreAndBulkDelete() {
        val names = listOf("Gabby AM", "Doxy AM", "Gabby PM", "TAD PM")
        val ids = names.map { name -> store.save(Tracker(0, name,
            ScaleSemantics.doneNotDoneLabels, 0, 8, 0, true,
            scaleType = ScaleSemantics.DONE_NOT_DONE)) }
        store.saveGroup(TrackerGroup(0, "Medication", 8, 0, true, ids,
            reminderSlots = listOf(
                GroupReminderSlot("am", "AM", 8, 0, true, ids.take(2)),
                GroupReminderSlot("pm", "PM", 20, 0, true, ids.takeLast(2))),
            multipleReminders = true))
        val backup = store.backup()
        assertTrue(backup.contains("\"multipleReminders\": true"))
        store.restore(backup)
        val restored = store.groups().single()
        assertTrue(restored.multipleReminders)
        assertEquals(listOf("am", "pm"), restored.reminderSlots.map { it.id })
        assertEquals(listOf(2, 2), restored.reminderSlots.map { it.trackerIds.size })
        val first = restored.reminderSlots.first().trackerIds.first()
        store.deleteTrackers(setOf(first))
        val after = store.groups().single()
        assertTrue(after.multipleReminders)
        assertEquals(listOf(1, 2), after.reminderSlots.map { it.trackerIds.size })
        assertEquals(listOf("am", "pm"), after.reminderSlots.map { it.id })
    }

    @Test fun v11MigrationPreservesLegacySingleReminder() {
        store.close()
        context.deleteDatabase("daygrid.db")
        val db = context.openOrCreateDatabase("daygrid.db", Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE trackers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,labels TEXT NOT NULL,palette INTEGER NOT NULL,hour INTEGER NOT NULL,minute INTEGER NOT NULL,reminder INTEGER NOT NULL,uuid TEXT NOT NULL UNIQUE,allow_past INTEGER NOT NULL DEFAULT 1,allow_future INTEGER NOT NULL DEFAULT 0,schedule TEXT NOT NULL DEFAULT 'daily',paused INTEGER NOT NULL DEFAULT 0,reminder_message TEXT NOT NULL DEFAULT '',scale_type TEXT NOT NULL DEFAULT 'custom')")
        db.execSQL("CREATE TABLE entries(tracker_id INTEGER NOT NULL,day TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 11),PRIMARY KEY(tracker_id,day),FOREIGN KEY(tracker_id) REFERENCES trackers(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE tracker_groups(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT UNIQUE,name TEXT,hour INTEGER,minute INTEGER,reminder INTEGER,paused INTEGER DEFAULT 0,reminder_message TEXT DEFAULT '')")
        db.execSQL("CREATE TABLE group_members(group_id INTEGER REFERENCES tracker_groups(id) ON DELETE CASCADE,tracker_id INTEGER REFERENCES trackers(id) ON DELETE CASCADE,position INTEGER,PRIMARY KEY(group_id,tracker_id))")
        db.execSQL("INSERT INTO trackers(id,name,labels,palette,hour,minute,reminder,uuid) VALUES(1,'Med','[\"Not done\",\"Done\"]',0,8,0,1,'11111111-1111-4111-8111-111111111111')")
        db.execSQL("INSERT INTO tracker_groups(id,uuid,name,hour,minute,reminder) VALUES(1,'22222222-2222-4222-8222-222222222222','Medication',8,0,1)")
        db.execSQL("INSERT INTO group_members VALUES(1,1,0)")
        db.version = 11
        db.close()
        store = Store(context)
        val group = store.groups().single()
        assertFalse(group.multipleReminders)
        assertTrue(group.reminderSlots.isEmpty())
        assertEquals(listOf(1L), group.effectiveReminderSlots().single().trackerIds)
    }
}
