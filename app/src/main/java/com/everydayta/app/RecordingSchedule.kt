package com.everydayta.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One due-date rule per tracker. Manual history edits are governed separately by entry permissions. */
data class RecordingSchedule(
    val kind: Kind = Kind.DAILY,
    val interval: Int = 1,
    val anchor: LocalDate = LocalDate.of(2020, 1, 6),
    val weekdays: Set<Int> = setOf(1), // ISO weekday: Monday=1 ... Sunday=7
    val monthDays: Set<Int> = setOf(1),
    val ordinal: Int = 1, // 1st–4th, or -1 for last
    val weekday: Int = 1
) {
    enum class Kind { DAILY, WEEKDAYS, EVERY_N_DAYS, MONTH_DATES, NTH_WEEKDAY, EVERY_N_WEEKS }

    fun description(): String = when (kind) {
        Kind.DAILY -> "Every day"
        Kind.WEEKDAYS -> "Selected weekdays"
        Kind.EVERY_N_DAYS -> "Every $interval days"
        Kind.MONTH_DATES -> "Selected monthly dates"
        Kind.NTH_WEEKDAY -> "${mapOf(-1 to "Last", 1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth").getValue(ordinal)} ${DayOfWeek.of(weekday).name.lowercase().replaceFirstChar { it.uppercase() }}"
        Kind.EVERY_N_WEEKS -> "Every $interval weeks"
    }

    fun validate() {
        require(interval in 1..365) { "Interval must be between 1 and 365" }
        require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) { "Select at least one weekday" }
        require(monthDays.isNotEmpty() && monthDays.all { it in 1..31 }) { "Select a valid day of the month" }
        require(ordinal in setOf(-1, 1, 2, 3, 4)) { "Choose first, second, third, fourth or last" }
        require(weekday in 1..7) { "Invalid weekday" }
    }

    fun dueOn(day: LocalDate): Boolean {
        validate()
        return when (kind) {
            Kind.DAILY -> true
            Kind.WEEKDAYS -> day.dayOfWeek.value in weekdays
            Kind.EVERY_N_DAYS -> day >= anchor && ChronoUnit.DAYS.between(anchor, day) % interval == 0L
            Kind.MONTH_DATES -> day.dayOfMonth in monthDays
            Kind.NTH_WEEKDAY -> {
                val first = YearMonth.from(day).atDay(1)
                val matching = if (ordinal == -1)
                    first.with(TemporalAdjusters.lastInMonth(DayOfWeek.of(weekday)))
                else first.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, DayOfWeek.of(weekday)))
                day == matching
            }
            Kind.EVERY_N_WEEKS -> {
                val anchorMonday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dayMonday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = ChronoUnit.WEEKS.between(anchorMonday, dayMonday)
                day >= anchor && weeks >= 0 && weeks % interval == 0L && day.dayOfWeek.value in weekdays
            }
        }
    }

    /** Compact, versioned, locale-independent format; no Android runtime needed to parse. */
    fun encode(): String {
        validate()
        return listOf("r1", kind.name, interval, anchor, weekdays.sorted().joinToString(","),
            monthDays.sorted().joinToString(","), ordinal, weekday).joinToString("|")
    }

    companion object {
        val DAILY = RecordingSchedule()
        fun decode(text: String): RecordingSchedule {
            if (text == "daily" || text.isBlank()) return DAILY
            val parts = text.split('|')
            require(parts.size == 8 && parts[0] == "r1") { "Unsupported recording schedule" }
            fun setOfInts(index: Int) = parts[index].split(',').map { it.toInt() }.toSet()
            return RecordingSchedule(Kind.valueOf(parts[1]), parts[2].toInt(),
                LocalDate.parse(parts[3]), setOfInts(4), setOfInts(5),
                parts[6].toInt(), parts[7].toInt()).also { it.validate() }
        }
    }
}

/** At most one check-in notification per tracker/date; explicit completion always wins. */
fun dueAndMissing(tracker: Tracker, day: LocalDate, scores: Map<Long, Int>): Boolean =
    !tracker.paused && tracker.schedule.dueOn(day) && scores[tracker.id] == null

/** Calculate the next *eligible* reminder time, including DST/time-zone transitions. */
fun nextDueReminder(now: java.time.ZonedDateTime, hour: Int, minute: Int,
                    predicate: (LocalDate) -> Boolean): java.time.ZonedDateTime? {
    require(hour in 0..23 && minute in 0..59)
    for (delta in 0..2500L) {
        val day = now.toLocalDate().plusDays(delta)
        if (!predicate(day)) continue
        val target = day.atTime(hour, minute).atZone(now.zone)
        if (target.isAfter(now)) return target
    }
    return null
}
