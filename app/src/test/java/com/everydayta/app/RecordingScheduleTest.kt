package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecordingScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun due(kind: RecordingSchedule.Kind, day: String, interval: Int = 1,
                    anchor: String = "2026-01-05", days: Set<Int> = setOf(1),
                    ordinal: Int = 1, weekday: Int = 1, monthDays: Set<Int> = setOf(1)) =
        RecordingSchedule(kind, interval, d(anchor), days, monthDays, ordinal, weekday).dueOn(d(day))

    @Test fun weekdays() {
        assertTrue(due(RecordingSchedule.Kind.WEEKDAYS,"2026-10-19", days=setOf(1,3,5)))
        assertFalse(due(RecordingSchedule.Kind.WEEKDAYS,"2026-10-20", days=setOf(1,3,5)))
    }
    @Test fun dayIntervalsRespectAnchor() {
        assertFalse(due(RecordingSchedule.Kind.EVERY_N_DAYS,"2026-01-04", interval=3))
        assertTrue(due(RecordingSchedule.Kind.EVERY_N_DAYS,"2026-01-05", interval=3))
        assertTrue(due(RecordingSchedule.Kind.EVERY_N_DAYS,"2026-01-08", interval=3))
        assertFalse(due(RecordingSchedule.Kind.EVERY_N_DAYS,"2026-01-09", interval=3))
    }
    @Test fun monthlyDatesSkipShortMonths() {
        assertTrue(due(RecordingSchedule.Kind.MONTH_DATES,"2028-02-29",monthDays=setOf(29,30)))
        assertTrue(due(RecordingSchedule.Kind.MONTH_DATES,"2026-03-30",monthDays=setOf(29,30)))
        assertFalse(due(RecordingSchedule.Kind.MONTH_DATES,"2026-02-28",monthDays=setOf(29,30)))
    }
    @Test fun firstAndLastWeekdayOfMonth() {
        assertTrue(due(RecordingSchedule.Kind.NTH_WEEKDAY,"2026-10-05",weekday=1))
        assertTrue(due(RecordingSchedule.Kind.NTH_WEEKDAY,"2026-10-30",ordinal=-1,weekday=5))
        assertFalse(due(RecordingSchedule.Kind.NTH_WEEKDAY,"2026-10-23",ordinal=-1,weekday=5))
    }
    @Test fun everyThirdSaturdayAfterAnchor() {
        val k=RecordingSchedule.Kind.EVERY_N_WEEKS
        assertTrue(due(k,"2026-01-10",3,days=setOf(6)))
        assertFalse(due(k,"2026-01-17",3,days=setOf(6)))
        assertTrue(due(k,"2026-01-31",3,days=setOf(6)))
    }
    @Test fun scheduleRoundTripAndMalformedReject() {
        val value=RecordingSchedule(RecordingSchedule.Kind.EVERY_N_WEEKS,3,d("2026-03-02"),setOf(2,6))
        assertEquals(value, RecordingSchedule.decode(value.encode()))
        assertEquals(RecordingSchedule.DAILY, RecordingSchedule.decode("daily"))
        assertThrows(IllegalArgumentException::class.java) { RecordingSchedule.decode("unknown") }
        assertThrows(IllegalArgumentException::class.java) { RecordingSchedule.decode(value.copy(interval=0).encode()) }
    }
    @Test fun nextReminderUsesDueDays() {
        val now=d("2026-10-19").atTime(21,0).atZone(ZoneId.of("Europe/London"))
        val at=nextDueReminder(now,20,0) { it.dayOfWeek.value==1 }
        assertEquals(d("2026-10-26"),at!!.toLocalDate())
    }
    @Test fun overlappingGroupsHaveOneOwner() {
        val t=Tracker(1,"X",listOf("Low","High"),0,20,0,true)
        val a=TrackerGroup(1,"AM",8,0,true,listOf(1))
        val b=TrackerGroup(2,"PM",20,0,true,listOf(1))
        assertEquals(setOf(1L),groupNotificationTrackerIds(a,listOf(a,b),listOf(t),d("2026-10-19"),emptyMap()))
        assertTrue(groupNotificationTrackerIds(b,listOf(a,b),listOf(t),d("2026-10-19"),emptyMap()).isEmpty())
        assertTrue(groupNotificationTrackerIds(a,listOf(a,b),listOf(t),d("2026-10-19"),mapOf(1L to 2)).isEmpty())
    }
}
