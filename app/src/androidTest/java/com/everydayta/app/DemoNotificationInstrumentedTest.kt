package com.everydayta.app

import android.content.Intent
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Demo-only intent/scale contract. Exercises no real tracker and writes no observations. */
@RunWith(AndroidJUnit4::class)
class DemoNotificationInstrumentedTest {
    private fun demoIntent(trackerId: Long, day: String = LocalDate.now().toString()) =
        Intent().putExtra(QuickCheckIn.EXTRA_DEMO, true)
            .putExtra(QuickCheckIn.EXTRA_GROUP, -1L)
            .putExtra(QuickCheckIn.EXTRA_TRACKER, trackerId)
            .putExtra(QuickCheckIn.EXTRA_DAY, day)

    @Test fun numericDemoUsesAllElevenChoicesWithoutPersistedIdentity() {
        val tracker = DemoCheckIn.numericTracker
        assertTrue(tracker.id < 0)
        assertEquals(ScaleSemantics.ZERO_TO_TEN, tracker.scaleType)
        assertEquals((0..10).map(Int::toString), tracker.labels)
        assertTrue(DemoCheckIn.isValidRequest(demoIntent(tracker.id)))
        assertEquals("0", QuickEntryRules.display(tracker, 1))
        assertEquals("10", QuickEntryRules.display(tracker, 11))
    }

    @Test fun binaryDemoOpensFocusedCheckInAndRejectsStaleIntents() {
        val tracker = DemoCheckIn.binaryTracker
        assertTrue(tracker.id < 0)
        assertEquals(listOf("Not done", "Done"), tracker.labels)
        assertTrue(DemoCheckIn.isValidRequest(demoIntent(tracker.id)))
        assertFalse(DemoCheckIn.isValidRequest(demoIntent(tracker.id, LocalDate.now().minusDays(1).toString())))
        assertFalse(DemoCheckIn.isValidRequest(demoIntent(123L)))
        assertFalse(DemoCheckIn.isValidRequest(demoIntent(tracker.id).putExtra(QuickCheckIn.EXTRA_DEMO, false)))
    }


    private fun groupIntent(session: Int, step: Int, trackerId: Long) =
        demoIntent(trackerId).putExtra(DemoCheckIn.EXTRA_GROUP_DEMO, true)
            .putExtra(DemoCheckIn.EXTRA_GROUP_SESSION, session)
            .putExtra(DemoCheckIn.EXTRA_GROUP_STEP, step)

    @Test fun demoGroupProgressesBinaryLikertNumericAndRejectsOldActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DemoCheckIn.startGroup(context)
        assertEquals(0, first.step)
        assertEquals(ScaleSemantics.DONE_NOT_DONE, first.tracker.scaleType)
        val binary = groupIntent(first.session, 0, first.tracker.id)
        assertFalse(DemoCheckIn.advanceGroup(context, binary, first.tracker.id, 2, true))
        assertTrue(DemoCheckIn.advanceGroup(context, binary, first.tracker.id, 2, false))
        assertFalse(DemoCheckIn.advanceGroup(context, binary, first.tracker.id, 2, true))

        val second = DemoCheckIn.activeGroupStep(context)!!
        assertEquals(1, second.step)
        assertEquals(ScaleSemantics.LIKERT, second.tracker.scaleType)
        val likert = groupIntent(first.session, 1, second.tracker.id)
        assertFalse(DemoCheckIn.advanceGroup(context, likert, second.tracker.id, 3, true))
        assertTrue(DemoCheckIn.advanceGroup(context, likert, second.tracker.id, 3, false))

        val third = DemoCheckIn.activeGroupStep(context)!!
        assertEquals(2, third.step)
        assertEquals(ScaleSemantics.ZERO_TO_TEN, third.tracker.scaleType)
        assertEquals("0", QuickEntryRules.display(third.tracker, 1))
        assertEquals("10", QuickEntryRules.display(third.tracker, 11))
        val numeric = groupIntent(first.session, 2, third.tracker.id)
        assertTrue(DemoCheckIn.advanceGroup(context, numeric, third.tracker.id, 11, false))
        assertNull(DemoCheckIn.activeGroupStep(context))
    }

    @Test fun newDemoSessionInvalidatesPreviousAndCannotBecomeRealTracker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val old = DemoCheckIn.startGroup(context)
        val oldIntent = groupIntent(old.session, old.step, old.tracker.id)
        val current = DemoCheckIn.startGroup(context)
        assertTrue(current.session != old.session)
        assertNull(DemoCheckIn.groupState(context, oldIntent))
        assertTrue(DemoCheckIn.demoGroup.all { it.id < 0L })
        assertFalse(DemoCheckIn.advanceGroup(context, oldIntent, old.tracker.id, 1, true))
    }

    @Test fun newNotificationsHaveNoDirectScoreButtons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // On a device without POST_NOTIFICATIONS permission this UI test is skipped.
        if (!ReminderNotifications.isAvailable(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(ReminderNotifications.postQuickEntryTest(context, binary = true))
        val binary = manager.activeNotifications.firstOrNull { it.tag == "demo-quick-entry" }
        assertNotNull(binary)
        assertTrue(binary!!.notification.actions.isNullOrEmpty())
        ReminderNotifications.dismissQuickEntryTest(context)

        assertTrue(ReminderNotifications.postGroupQuickEntryTest(context))
        val group = manager.activeNotifications.firstOrNull { it.tag == "demo-group-quick-entry" }
        assertNotNull(group)
        assertTrue(group!!.notification.actions.isNullOrEmpty())
        manager.cancel("demo-group-quick-entry", 0x45DD)
    }
}
