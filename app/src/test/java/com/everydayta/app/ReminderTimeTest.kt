package com.everydayta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {
    private val zone = ZoneId.of("Europe/London")

    @Test fun futureToday() {
        val now = ZonedDateTime.of(2026, 10, 18, 12, 0, 0, 0, zone)
        val next = nextReminder(now, 20, 0)
        assertEquals(18, next.dayOfMonth)
        assertEquals(20, next.hour)
    }

    @Test fun missedTodaySchedulesTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 18, 21, 0, 0, 0, zone)
        val next = nextReminder(now, 20, 0)
        assertEquals(19, next.dayOfMonth)
        assertEquals(20, next.hour)
    }

    @Test fun exactMinuteSchedulesTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone)
        assertEquals(19, nextReminder(now, 20, 0).dayOfMonth)
    }

    @Test fun invalidTimeRejected() {
        try {
            nextReminder(ZonedDateTime.now(zone), 24, 0)
            throw AssertionError("Invalid hour accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun dstTransitionStillSchedulesFuture() {
        val now = ZonedDateTime.of(2026, 10, 25, 1, 30, 0, 0, zone)
        assertTrue(nextReminder(now, 20, 0).isAfter(now))
    }
}
