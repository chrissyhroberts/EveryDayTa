package com.everydayta.app

import java.time.ZonedDateTime

/** Compute the next local daily reminder, respecting the device's current time zone. */
internal fun nextReminder(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    require(hour in 0..23 && minute in 0..59)
    var at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!at.isAfter(now)) at = at.plusDays(1)
    return at
}
