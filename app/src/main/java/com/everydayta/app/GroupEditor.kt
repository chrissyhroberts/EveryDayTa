package com.everydayta.app

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GroupEditor(
    existing: TrackerGroup?,
    trackers: List<Tracker>,
    onCancel: () -> Unit,
    onSave: (TrackerGroup) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    val identity = existing?.uuid
    var name by remember(identity) { mutableStateOf(existing?.name.orEmpty()) }
    val selected = remember(identity) { mutableStateListOf<Long>().apply { addAll(existing?.trackerIds.orEmpty()) } }
    var reminder by remember(identity) { mutableStateOf(existing?.reminder ?: true) }
    var paused by remember(identity) { mutableStateOf(existing?.paused ?: false) }
    var reminderMessage by remember(identity) { mutableStateOf(existing?.reminderMessage.orEmpty()) }
    var hour by remember(identity) { mutableIntStateOf(existing?.hour ?: 20) }
    var minute by remember(identity) { mutableIntStateOf(existing?.minute ?: 0) }
    var multipleReminders by remember(identity) { mutableStateOf(existing?.multipleReminders == true) }
    val slots = remember(identity) { mutableStateListOf<GroupReminderSlot>().apply {
        addAll(existing?.reminderSlots.orEmpty())
    } }
    var confirmDelete by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val proposed = TrackerGroup(existing?.id ?: 0L, name.trim(), hour, minute,
        reminder, selected.toList(), existing?.uuid ?: "new", paused, reminderMessage.trim(),
        if (multipleReminders) slots.toList() else emptyList(), multipleReminders)
    val slotError = if (!multipleReminders) null else when {
        slots.isEmpty() -> "Add at least one reminder, or switch to a single reminder"
        slots.size > 12 -> "Maximum 12 reminders"
        slots.any { it.trackerIds.isEmpty() } -> "Assign at least one tracker to every reminder"
        slots.flatMap { it.trackerIds }.size != slots.flatMap { it.trackerIds }.distinct().size ->
            "A tracker can only belong to one reminder within this group"
        slots.any { it.name.isBlank() || it.name.length > 100 } -> "Each reminder needs a short name"
        else -> null
    }
    val valid = name.trim().isNotEmpty() && name.trim().length <= 200 &&
        selected.isNotEmpty() && slotError == null

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(if (existing == null) "New check-in group" else "Edit check-in group",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = DayStyle.ink)
                Spacer(Modifier.height(4.dp))
                Text("Choose the trackers you want to complete together.", color = DayStyle.subdued)
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GROUP NAME", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = DayStyle.subdued)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("e.g. Evening check-in") },
                            isError = attempted && name.isBlank(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            item {
                Text("TRACKERS  ·  ${selected.size} SELECTED", color = DayStyle.subdued,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            if (trackers.isEmpty()) {
                item {
                    Text("Create a tracker first, then come back to add it to this group.",
                        color = DayStyle.subdued)
                }
            }
            items(trackers, key = { it.id }) { tracker ->
                val checked = tracker.id in selected
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (checked) {
                            selected.remove(tracker.id)
                            slots.indices.forEach { i ->
                                slots[i] = slots[i].copy(trackerIds = slots[i].trackerIds - tracker.id)
                            }
                        } else selected.add(tracker.id)
                    },
                    color = if (checked) DayStyle.paleTeal else DayStyle.surface,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, if (checked) DayStyle.teal else DayStyle.outline)
                ) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked,
                            onCheckedChange = { checkedNow ->
                                if (checkedNow) {
                                    if (tracker.id !in selected) selected.add(tracker.id)
                                } else {
                                    selected.remove(tracker.id)
                                    slots.indices.forEach { i ->
                                        slots[i] = slots[i].copy(trackerIds = slots[i].trackerIds - tracker.id)
                                    }
                                }
                            })
                        Spacer(Modifier.width(7.dp))
                        Text(tracker.name, Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                            Surface(color = Color(Palettes.color(tracker, tracker.labels.size)),
                                shape = RoundedCornerShape(4.dp)) { Spacer(Modifier.size(12.dp)) }
                        }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("REMINDER ARRANGEMENT", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = DayStyle.subdued)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Multiple reminder times", fontWeight = FontWeight.SemiBold)
                                Text("e.g. AM medicines and PM medicines in one group",
                                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                            }
                            Switch(checked = multipleReminders, onCheckedChange = { value ->
                                if (value && slots.isEmpty()) {
                                    slots.add(GroupReminderSlot(java.util.UUID.randomUUID().toString(),
                                        "Check-in", hour, minute, reminder, selected.toList(), reminderMessage))
                                }
                                multipleReminders = value
                            })
                        }
                        if (!multipleReminders) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("One group reminder", fontWeight = FontWeight.SemiBold)
                                    Text("For all due, incomplete members",
                                        style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                                }
                                Switch(checked = reminder, onCheckedChange = { reminder = it })
                            }
                            if (reminder) {
                                OutlinedTextField(value = reminderMessage,
                                    onValueChange = { reminderMessage = it.take(240) },
                                    label = { Text("Custom notification message") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
                                OutlinedButton(onClick = {
                                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m },
                                        hour, minute, true).show()
                                }) { Text("Reminder time · %02d:%02d".format(hour, minute)) }
                            }
                        } else {
                            Text("Give each reminder a time and choose its trackers. A tracker can be assigned to only one reminder in this group.",
                                color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Membership suppresses individual reminders even if this group is paused or its alerts are disabled.",
                            color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (multipleReminders) {
                items(slots.size, key = { index -> slots[index].id }) { index ->
                    val slot = slots[index]
                    DayPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("REMINDER ${index + 1}", Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DayStyle.subdued, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { slots.removeAt(index) }) { Text("Remove") }
                            }
                            OutlinedTextField(value = slot.name,
                                onValueChange = { slots[index] = slot.copy(name = it.take(100)) },
                                label = { Text("Reminder name") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Enabled", Modifier.weight(1f))
                                Switch(checked = slot.enabled,
                                    onCheckedChange = { slots[index] = slot.copy(enabled = it) })
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        slots[index] = slots[index].copy(hour = h, minute = m)
                                    }, slot.hour, slot.minute, true).show()
                                }) { Text("%02d:%02d".format(slot.hour, slot.minute)) }
                            }
                            OutlinedTextField(value = slot.message,
                                onValueChange = { slots[index] = slot.copy(message = it.take(240)) },
                                label = { Text("Custom message (optional)") },
                                modifier = Modifier.fillMaxWidth(), maxLines = 3)
                            Text("INCLUDED TRACKERS", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = DayStyle.subdued)
                            trackers.filter { it.id in selected }.forEach { tracker ->
                                val assignedHere = tracker.id in slot.trackerIds
                                val assignedElsewhere = slots.any { it.id != slot.id && tracker.id in it.trackerIds }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) {
                                    Checkbox(checked = assignedHere,
                                        enabled = assignedHere || !assignedElsewhere,
                                        onCheckedChange = { checked ->
                                            slots[index] = slot.copy(trackerIds = if (checked)
                                                slot.trackerIds + tracker.id else slot.trackerIds - tracker.id)
                                        })
                                    Text(tracker.name, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (assignedElsewhere) DayStyle.subdued else DayStyle.ink)
                                }
                            }
                            if (slot.trackerIds.isEmpty()) {
                                Text("Select at least one tracker", color = DayStyle.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        if (slots.size < 12) slots.add(GroupReminderSlot(
                            java.util.UUID.randomUUID().toString(), "Reminder ${slots.size + 1}",
                            20, 0, true, emptyList()))
                    }, modifier = Modifier.fillMaxWidth(), enabled = slots.size < 12) {
                        Text("+ Add reminder time")
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pause this group", fontWeight = FontWeight.SemiBold)
                            Text("Keeps members and history. Pauses this group's alerts without restarting individual reminders. Other groups are unaffected.",
                                color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = paused, onCheckedChange = { paused = it })
                    }
                }
            }
            if (existing != null) {
                item {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete group", color = DayStyle.error)
                    }
                    Text("Keeps every tracker and recorded score. Members without another group can resume their individual reminders.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (attempted && !valid) {
            Text(if (name.isBlank()) "Give the group a name" else if (selected.isEmpty())
                "Select at least one tracker" else slotError ?: "Group name is too long",
                Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = DayStyle.error)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    attempted = true
                    if (valid) onSave(proposed.copy(uuid =
                        existing?.uuid ?: java.util.UUID.randomUUID().toString()))
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (existing == null) "Create group" else "Save group") }
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("The group and its reminder will be removed. Trackers and scores stay intact. Individual reminders may resume for trackers no longer in any group.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing.id) }) {
                Text("Delete group", color = DayStyle.error)
            } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
