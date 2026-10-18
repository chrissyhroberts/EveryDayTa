package com.everydayta.app

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val defaultLabels = presetLabels(0)


@Composable
fun TrackerEditor(
    existing: Tracker?,
    canEditLabels: Boolean,
    existingNames: List<Pair<Long, String>> = emptyList(),
    onCancel: () -> Unit,
    onSave: (Tracker) -> Unit
) {
    val context = LocalContext.current
    val identity = existing?.uuid
    var name by remember(identity) { mutableStateOf(existing?.name ?: "") }
    val labels = remember(identity) {
        mutableStateListOf<String>().apply { addAll(existing?.labels ?: defaultLabels) }
    }
    // Presets are conveniences: actual stored values are always explicit ordered labels.
    var preset by remember(identity) {
        mutableIntStateOf(
            when (existing?.scaleType) {
                null, ScaleSemantics.LIKERT -> 0
                ScaleSemantics.ZERO_TO_TEN -> 1
                ScaleSemantics.DONE_NOT_DONE -> 2
                else -> 3
            }
        )
    }
    var expandLabels by remember(identity) { mutableStateOf(false) }
    var palette by remember(identity) { mutableIntStateOf(existing?.palette ?: 0) }
    var reminder by remember(identity) { mutableStateOf(existing?.reminder ?: false) }
    var reminderMessage by remember(identity) { mutableStateOf(existing?.reminderMessage.orEmpty()) }
    var paused by remember(identity) { mutableStateOf(existing?.paused ?: false) }
    var allowPastEntries by remember(identity) { mutableStateOf(existing?.allowPastEntries ?: true) }
    var allowFutureEntries by remember(identity) { mutableStateOf(existing?.allowFutureEntries ?: false) }
    var hour by remember(identity) { mutableIntStateOf(existing?.hour ?: 20) }
    var minute by remember(identity) { mutableIntStateOf(existing?.minute ?: 0) }
    var schedule by remember(identity) { mutableStateOf(existing?.schedule ?: RecordingSchedule.DAILY) }
    var attempted by remember(identity) { mutableStateOf(false) }

    val duplicateName = !TrackerNameRules.isUnique(name, existingNames, existing?.id ?: 0L)
    val problem = trackerFormError(name, labels) ?: if (duplicateName)
        "A tracker with this name already exists." else null
    val time = "%02d:%02d".format(hour, minute)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (existing == null) "Make it yours" else "Tracker settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = DayStyle.ink)
                    Text(
                        if (existing == null) "Choose what to track and how you want to record it."
                        else "Update how this tracker looks and when it reminds you.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorSectionTitle("01", "What are we tracking?")
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            isError = attempted && (name.isBlank() || name.trim().length > 200 || duplicateName),
                            label = { Text("Tracker name") },
                            placeholder = { Text("e.g. Energy, mood, pain, exercise") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        if (duplicateName) Text("A tracker with this name already exists.",
                            color = DayStyle.error, style = MaterialTheme.typography.bodySmall)
                        Text("You'll see this name on your home screen and calendar.",
                            color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EditorSectionTitle("02", "How will you score it?")
                        Text("START WITH A SCALE", style = MaterialTheme.typography.labelSmall,
                            color = DayStyle.subdued, letterSpacing = 1.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Likert", "0–10", "Done / Not done", "Custom").forEachIndexed { index, title ->
                                val chosen = preset == index
                                Surface(
                                    modifier = Modifier.widthIn(min = 87.dp).heightIn(min = 46.dp)
                                        .clickable(enabled = canEditLabels) {
                                            preset = index
                                            labels.clear()
                                            labels.addAll(presetLabels(index))
                                            expandLabels = index == 3
                                        },
                                    shape = RoundedCornerShape(13.dp),
                                    color = if (chosen) DayStyle.paleTeal else DayStyle.surface,
                                    border = BorderStroke(if (chosen) 2.dp else 1.dp,
                                        if (chosen) DayStyle.teal else DayStyle.outline)
                                ) {
                                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(title, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                            color = if (chosen) DayStyle.teal else DayStyle.ink)
                                    }
                                }
                            }
                        }
                        if (preset == 3 && canEditLabels) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Number of levels", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { if (labels.size > 2) labels.removeAt(labels.lastIndex) },
                                    modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp),
                                    enabled = labels.size > 2) { Text("−", fontSize = 24.sp) }
                                Text("${labels.size}", Modifier.width(44.dp), textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { if (labels.size < 11) labels.add("Level ${labels.size + 1}") },
                                    modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp),
                                    enabled = labels.size < 11) { Text("+", fontSize = 22.sp) }
                            }
                        }
                        Text(if (preset == 1) "Scores 0–10 · 11 choices" else "${labels.size} choices · from lowest to highest",
                            color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            labels.forEachIndexed { i, _ ->
                                Box(Modifier.weight(1f).height(26.dp)
                                    .background(Color(Palettes.colorFor(palette, i + 1, labels.size)),
                                        RoundedCornerShape(5.dp)))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels.firstOrNull().orEmpty(), color = DayStyle.subdued,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(labels.lastOrNull().orEmpty(), color = DayStyle.subdued,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (canEditLabels) {
                            TextButton(onClick = { expandLabels = !expandLabels },
                                contentPadding = PaddingValues(0.dp)) {
                                Text(if (expandLabels) "Hide level labels  ↑" else "Edit level labels  ↓")
                            }
                            if (expandLabels) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    labels.indices.forEach { index ->
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(Modifier.size(16.dp).background(
                                                Color(Palettes.colorFor(palette, index + 1, labels.size)),
                                                CircleShape))
                                            OutlinedTextField(
                                                value = labels[index],
                                                onValueChange = {
                                                    labels[index] = it
                                                    if ((preset == 0 && labels != ScaleSemantics.likertLabels) ||
                                                        (preset == 1 && labels != ScaleSemantics.zeroToTenLabels) ||
                                                        (preset == 2 && labels != ScaleSemantics.doneNotDoneLabels)) {
                                                        preset = 3
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                label = { Text(if (preset == 1) "Score $index" else "Level ${index + 1}") }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Scoring labels are locked for existing trackers to protect recorded history.",
                                color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        EditorSectionTitle("03", "Choose your colours")
                        Text("Each score gets a shade, making patterns visible at a glance.",
                            color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        (0..2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                (0..1).forEach { col ->
                                    val i = row * 2 + col
                                    if (i < Palettes.names.size) {
                                        PaletteOption(i, palette == i, Modifier.weight(1f)) { palette = i }
                                    } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorSectionTitle("04", "A gentle reminder")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily check-in", fontWeight = FontWeight.SemiBold)
                                Text("An optional nudge to record your day.", color = DayStyle.subdued,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = reminder, onCheckedChange = { reminder = it })
                        }
                        if (reminder) {
                            OutlinedTextField(
                                value = reminderMessage, onValueChange = { reminderMessage = it.take(240) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Custom notification message") },
                                placeholder = { Text("e.g. How is your energy today?") },
                                supportingText = { Text("Optional · ${reminderMessage.length}/240 characters") },
                                minLines = 2, maxLines = 3
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m },
                                        hour, minute, true).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = DayStyle.paleTeal
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Remind me at", modifier = Modifier.weight(1f),
                                        color = DayStyle.ink)
                                    Text(time, color = DayStyle.teal, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Change ›", color = DayStyle.teal,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorSectionTitle("05", "When is an entry due?")
                        Text("Only scheduled days appear as due on Today or trigger reminders. You can still enter other days manually.",
                            style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                        RecordingScheduleEditor(schedule) { schedule = it }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorSectionTitle("07", "Tracker status")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (paused) "Paused" else "Active", fontWeight = FontWeight.SemiBold)
                                Text(if (paused) "Saved history is kept. Check-ins and reminders are paused."
                                    else "Included in scheduled check-ins and reminders.",
                                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                            }
                            Switch(checked = !paused, onCheckedChange = { paused = !it })
                        }
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EditorSectionTitle("06", "Which dates can be edited?")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Past dates", fontWeight = FontWeight.SemiBold)
                                Text("Allow entering or correcting earlier days.",
                                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                            }
                            Switch(checked = allowPastEntries, onCheckedChange = { allowPastEntries = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Future dates", fontWeight = FontWeight.SemiBold)
                                Text("Allow scores on upcoming days.",
                                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                            }
                            Switch(checked = allowFutureEntries, onCheckedChange = { allowFutureEntries = it })
                        }
                        Text("These controls never hide or delete saved observations. Today is always editable.",
                            style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                    }
                }
            }
        }
        Surface(color = DayStyle.surface, shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (attempted && problem != null) {
                    Text(problem, color = DayStyle.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(15.dp)) { Text("Cancel") }
                    Button(
                        onClick = {
                            attempted = true
                            if (problem == null) {
                                onSave(Tracker(existing?.id ?: 0L, name.trim(), labels.toList(),
                                    palette, hour, minute, reminder,
                                    existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                                    allowPastEntries, allowFutureEntries, schedule, paused, reminderMessage.trim(),
                                    when (preset) {
                                        0 -> ScaleSemantics.LIKERT
                                        1 -> ScaleSemantics.ZERO_TO_TEN
                                        2 -> ScaleSemantics.DONE_NOT_DONE
                                        else -> ScaleSemantics.CUSTOM
                                    }))
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) { Text(if (existing == null) "Create tracker" else "Save changes",
                        fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionTitle(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = DayStyle.paleTeal) {
            Text(number, Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium, color = DayStyle.teal,
                fontWeight = FontWeight.Bold)
        }
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = DayStyle.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaletteOption(index: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) DayStyle.paleTeal else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) DayStyle.teal else DayStyle.outline)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Palettes.shades[index].forEach { shade ->
                    Box(Modifier.weight(1f).height(24.dp)
                        .background(Color(shade.toInt()), RoundedCornerShape(4.dp)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Palettes.names[index], modifier = Modifier.weight(1f),
                    color = DayStyle.ink, style = MaterialTheme.typography.labelLarge)
                if (selected) Text("✓", color = DayStyle.teal, fontWeight = FontWeight.Bold)
            }
        }
    }
}
