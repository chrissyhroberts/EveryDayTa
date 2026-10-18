package com.everydayta.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Device tests for the exact read/eligibility/write path used by notification check-ins. */
@RunWith(AndroidJUnit4::class)
class QuickEntryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: Store

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("daygrid.db")
        store = Store(context)
    }
    @After fun clean() { store.close(); context.deleteDatabase("daygrid.db") }

    @Test fun notificationFlowAdvancesAMThenEndsWithoutTouchingPM() {
        val a = store.save(Tracker(0,"Gabby AM",ScaleSemantics.doneNotDoneLabels,0,8,0,true))
        val b = store.save(Tracker(0,"Doxy AM",ScaleSemantics.doneNotDoneLabels,0,8,0,true))
        val c = store.save(Tracker(0,"Gabby PM",ScaleSemantics.doneNotDoneLabels,0,20,0,true))
        val groupId = store.saveGroup(TrackerGroup(0,"Medication",8,0,true,listOf(a,b,c),
            reminderSlots=listOf(
                GroupReminderSlot("am", "AM",8,0,true,listOf(a,b)),
                GroupReminderSlot("pm", "PM",20,0,true,listOf(c))
            ),multipleReminders=true))
        val today = LocalDate.now()
        fun pending(slot: String) = QuickCheckIn.current(store,groupId,slot,-1L,today.toString())!!.pending.map { it.id }
        assertEquals(listOf(a,b),pending("am"))
        store.score(a,today,2)
        assertEquals(listOf(b),pending("am"))
        store.score(b,today,1) // Explicit Not done is recorded, not missing.
        assertTrue(pending("am").isEmpty())
        assertEquals(listOf(c),pending("pm"))
        assertNull(QuickCheckIn.current(store,groupId,"am",-1L,today.minusDays(1).toString()))
    }

    @Test fun oldNotificationIsRejectedAfterGroupPausedOrMembershipChanges() {
        val id = store.save(Tracker(0,"Mood",ScaleSemantics.likertLabels,0,9,0,true))
        val groupId = store.saveGroup(TrackerGroup(0,"Daily",9,0,true,listOf(id)))
        val day = LocalDate.now().toString()
        assertEquals(id, QuickCheckIn.current(store,groupId,"legacy",-1L,day)!!.pending.single().id)
        val group = store.groups().single()
        store.saveGroup(group.copy(paused=true))
        assertTrue(QuickCheckIn.current(store,groupId,"legacy",-1L,day)!!.pending.isEmpty())
        assertTrue(QuickCheckIn.current(store,-1L,null,id,day)!!.pending.isEmpty())
    }
}
