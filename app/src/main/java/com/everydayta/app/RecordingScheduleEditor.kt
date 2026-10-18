package com.everydayta.app

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val scheduleKinds = listOf(
    RecordingSchedule.Kind.DAILY to "Every day",
    RecordingSchedule.Kind.WEEKDAYS to "Selected weekdays",
    RecordingSchedule.Kind.EVERY_N_DAYS to "Every N days",
    RecordingSchedule.Kind.MONTH_DATES to "Dates each month",
    RecordingSchedule.Kind.NTH_WEEKDAY to "Nth weekday of month",
    RecordingSchedule.Kind.EVERY_N_WEEKS to "Every N weeks"
)
private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun RecordingScheduleEditor(value: RecordingSchedule, onChange: (RecordingSchedule) -> Unit) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val modeName = scheduleKinds.first { it.first == value.kind }.second
    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$modeName  ▾")
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        scheduleKinds.forEach { (kind, label) ->
            DropdownMenuItem(text = { Text(label) }, onClick = {
                menuExpanded = false
                val anchor = if (kind == RecordingSchedule.Kind.EVERY_N_DAYS ||
                    kind == RecordingSchedule.Kind.EVERY_N_WEEKS) LocalDate.now() else value.anchor
                onChange(value.copy(kind = kind, anchor = anchor))
            })
        }
    }
    when (value.kind) {
        RecordingSchedule.Kind.DAILY -> Unit
        RecordingSchedule.Kind.WEEKDAYS, RecordingSchedule.Kind.EVERY_N_WEEKS -> {
            Text("Days of the week", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                weekdays.forEachIndexed { index, title ->
                    val number = index + 1
                    val selected = number in value.weekdays
                    FilterChip(selected = selected,
                        onClick = {
                            val next = if (selected) value.weekdays - number else value.weekdays + number
                            if (next.isNotEmpty()) onChange(value.copy(weekdays = next))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(title.take(1)) }
                    )
                }
            }
            Text("${value.weekdays.sorted().joinToString { weekdays[it - 1] }}",
                style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
        }
        RecordingSchedule.Kind.MONTH_DATES -> {
            Text("Days of the month (unavailable dates are skipped)",
                style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
            (1..31).chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { n ->
                        val selected = n in value.monthDays
                        FilterChip(selected = selected, modifier = Modifier.weight(1f),
                            onClick = {
                                val next = if (selected) value.monthDays - n else value.monthDays + n
                                if (next.isNotEmpty()) onChange(value.copy(monthDays = next))
                            }, label = { Text(n.toString()) })
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        RecordingSchedule.Kind.NTH_WEEKDAY -> {
            var ordinalOpen by remember { mutableStateOf(false) }
            var dayOpen by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { ordinalOpen = true }) {
                        Text(mapOf(-1 to "Last", 1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth").getValue(value.ordinal))
                    }
                    DropdownMenu(expanded = ordinalOpen, onDismissRequest = { ordinalOpen = false }) {
                        listOf(1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth", -1 to "Last").forEach { (n,label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onChange(value.copy(ordinal = n)); ordinalOpen = false
                            })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { dayOpen = true }) { Text(weekdays[value.weekday - 1]) }
                    DropdownMenu(expanded = dayOpen, onDismissRequest = { dayOpen = false }) {
                        weekdays.forEachIndexed { index,label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onChange(value.copy(weekday = index + 1)); dayOpen = false
                            })
                        }
                    }
                }
            }
        }
        RecordingSchedule.Kind.EVERY_N_DAYS -> Unit
    }
    if (value.kind == RecordingSchedule.Kind.EVERY_N_DAYS ||
        value.kind == RecordingSchedule.Kind.EVERY_N_WEEKS) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (value.kind == RecordingSchedule.Kind.EVERY_N_DAYS) "Every" else "Every", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (value.interval > 1) onChange(value.copy(interval = value.interval - 1)) }) {
                Text("−")
            }
            Text("${value.interval} ${if (value.kind == RecordingSchedule.Kind.EVERY_N_DAYS) "days" else "weeks"}")
            OutlinedButton(onClick = {
                if (value.interval < if (value.kind == RecordingSchedule.Kind.EVERY_N_DAYS) 365 else 52)
                    onChange(value.copy(interval = value.interval + 1))
            }) { Text("+") }
        }
        OutlinedButton(onClick = {
            val d = value.anchor
            DatePickerDialog(context, { _,y,m,day -> onChange(value.copy(anchor = LocalDate.of(y,m+1,day))) },
                d.year,d.monthValue-1,d.dayOfMonth).show()
        }) { Text("Starting ${value.anchor}  ·  Change anchor") }
        if (value.kind == RecordingSchedule.Kind.EVERY_N_WEEKS) Text(
            "Weeks count from the Monday of the anchor week. Only selected weekdays are due.",
            style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
    }
}
