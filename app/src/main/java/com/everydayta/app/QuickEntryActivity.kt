package com.everydayta.app

import android.app.NotificationManager
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A short-lived, separate task. Notification → score → next score → Android launcher.
 * It never navigates to the full EveryDayTa UI or adds a screen to its back stack.
 */
class QuickEntryActivity : ComponentActivity() {
    private val groupId by lazy { intent.getLongExtra(QuickCheckIn.EXTRA_GROUP, -1L) }
    private val slotId by lazy { intent.getStringExtra(QuickCheckIn.EXTRA_SLOT) }
    private val trackerId by lazy { intent.getLongExtra(QuickCheckIn.EXTRA_TRACKER, -1L) }
    private val requestedDay by lazy { intent.getStringExtra(QuickCheckIn.EXTRA_DAY) }
    private val isDemo by lazy { intent.getBooleanExtra(QuickCheckIn.EXTRA_DEMO, false) }
    private val isGroupDemo by lazy { DemoCheckIn.isGroupDemo(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EveryDayTaTheme {
                var state by remember { mutableStateOf<QuickEntryState?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var saving by remember { mutableStateOf(false) }
                var completed by remember { mutableIntStateOf(0) }
                var initialCount by remember { mutableIntStateOf(0) }

                fun refresh() {
                    lifecycleScope.launch {
                        val loaded = if (isGroupDemo) {
                            DemoCheckIn.groupState(this@QuickEntryActivity, intent)?.let { step ->
                                initialCount = DemoCheckIn.demoGroup.size
                                completed = step.step
                                QuickEntryState("Test 10:00 group · nothing saved", listOf(step.tracker))
                            }
                        } else if (isDemo) {
                            if (DemoCheckIn.isValidRequest(intent))
                                QuickEntryState("Test check-in · nothing saved",
                                    listOfNotNull(DemoCheckIn.trackerFor(trackerId)))
                            else null
                        } else withContext(Dispatchers.IO) {
                            Store(this@QuickEntryActivity).use { db ->
                                QuickCheckIn.current(db, groupId, slotId, trackerId, requestedDay)
                            }
                        }
                        if (loaded == null || loaded.pending.isEmpty()) {
                            finishAndRemoveTask()
                        } else {
                            if (initialCount == 0) initialCount = loaded.pending.size
                            state = loaded
                        }
                    }
                }
                LaunchedEffect(groupId, slotId, trackerId, requestedDay) { refresh() }
                BackHandler { finishAndRemoveTask() }

                val current = state?.pending?.firstOrNull()
                Surface(Modifier.fillMaxSize(), color = DayStyle.canvas) {
                    Column(
                        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("EveryDayTa", style = MaterialTheme.typography.titleMedium,
                                color = DayStyle.teal, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { finishAndRemoveTask() }) { Text("Close") }
                        }
                        if (current == null) {
                            CircularProgressIndicator()
                        } else {
                            Text(state?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                                color = DayStyle.subdued)
                            Text("${completed + 1} of ${initialCount.coerceAtLeast(completed + 1)}",
                                style = MaterialTheme.typography.labelLarge, color = DayStyle.subdued)
                            Text(current.name, style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold, color = DayStyle.ink)
                            Text(when (current.scaleType) {
                                ScaleSemantics.DONE_NOT_DONE -> "Did you complete it?"
                                ScaleSemantics.ZERO_TO_TEN -> "Choose a score from 0 to 10"
                                ScaleSemantics.LIKERT -> "How would you rate today?"
                                else -> "Choose a level"
                            }, style = MaterialTheme.typography.bodyLarge,
                                color = DayStyle.subdued)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                current.labels.indices.toList().chunked(
                                    if (current.labels.size > 6) 4 else 1
                                ).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        row.forEach { index ->
                                            val level = index + 1
                                            Button(
                                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                                enabled = !saving,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = androidx.compose.ui.graphics.Color(Palettes.color(current, level))
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                onClick = {
                                                    if (!saving) {
                                                        if (isGroupDemo) {
                                                            val accepted = DemoCheckIn.advanceGroup(
                                                                this@QuickEntryActivity, intent, current.id, level, false)
                                                            if (accepted) {
                                                                ReminderNotifications.updateGroupQuickEntryTest(this@QuickEntryActivity)
                                                                refresh()
                                                            } else {
                                                                error = "Test session changed. Please start another test."
                                                            }
                                                        } else if (isDemo) {
                                                            ReminderNotifications.dismissQuickEntryTest(this@QuickEntryActivity)
                                                            android.widget.Toast.makeText(this@QuickEntryActivity,
                                                                "Test complete · nothing saved", android.widget.Toast.LENGTH_SHORT).show()
                                                            finishAndRemoveTask()
                                                        } else {
                                                        saving = true
                                                        error = null
                                                        lifecycleScope.launch {
                                                            val result = withContext(Dispatchers.IO) {
                                                                runCatching {
                                                                    synchronized(QuickCheckIn) {
                                                                        Store(this@QuickEntryActivity).use { db ->
                                                                        // Re-check today's due ownership before committing.
                                                                        val fresh = QuickCheckIn.current(db, groupId, slotId,
                                                                            trackerId, requestedDay)
                                                                        require(fresh?.pending?.firstOrNull()?.id == current.id) {
                                                                            "This check-in has already changed."
                                                                        }
                                                                        db.score(current.id, LocalDate.now(), level)
                                                                        QuickCheckIn.updateNotification(
                                                                            this@QuickEntryActivity, db, groupId, slotId, trackerId)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            saving = false
                                                            result.onSuccess { completed++; refresh() }
                                                                .onFailure { error = it.message ?: "Unable to save"; refresh() }
                                                        }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(QuickEntryRules.display(current, level),
                                                    textAlign = TextAlign.Center,
                                                    color = if (androidx.compose.ui.graphics.Color(
                                                        Palettes.color(current, level)).luminance() < 0.45f)
                                                        androidx.compose.ui.graphics.Color.White
                                                    else androidx.compose.ui.graphics.Color.Black)
                                            }
                                        }
                                        repeat((if (current.labels.size > 6) 4 else 1) - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { finishAndRemoveTask() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Finish later")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class QuickEntryState(val title: String, val pending: List<Tracker>)

/** Shared resolution for notification actions and the focused Activity; never trusts stale extras. */
object QuickCheckIn {
    const val EXTRA_GROUP = "quick_group"
    const val EXTRA_SLOT = "quick_slot"
    const val EXTRA_TRACKER = "quick_tracker"
    const val EXTRA_DAY = "quick_day"
    const val ACTION_INLINE = "com.everydayta.app.QUICK_INLINE"
    const val EXTRA_DEMO = "quick_demo"

    internal fun current(db: Store, groupId: Long, slotId: String?, trackerId: Long, day: String?): QuickEntryState? {
        // A notification sent yesterday must never be used to record today's score.
        if (day != LocalDate.now().toString()) return null
        val groups = db.groups()
        val trackers = db.trackers()
        val scores = db.scoresOn(LocalDate.now())
        if (groupId >= 0) {
            val group = groups.firstOrNull { it.id == groupId } ?: return null
            val slot = group.effectiveReminderSlots().firstOrNull { it.id == slotId } ?: return null
            val due = QuickEntryRules.pending(group, slot, groups, trackers, LocalDate.now(), scores)
            return QuickEntryState(if (slot.id == "legacy") group.name else "${group.name} · ${slot.name}", due)
        }
        val due = QuickEntryRules.pending(null, null, groups, trackers, LocalDate.now(), scores, trackerId)
        return QuickEntryState("Daily check-in", due)
    }

    fun updateNotification(context: android.content.Context, db: Store,
                           groupId: Long, slotId: String?, trackerId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (groupId >= 0) {
            val group = db.groups().firstOrNull { it.id == groupId } ?: return
            val slot = group.effectiveReminderSlots().firstOrNull { it.id == slotId } ?: return
            val groups = db.groups()
            val trackers = db.trackers()
            val today = LocalDate.now()
            val scores = db.scoresOn(today)
            val missing = groupNotificationTrackerIds(group, slot, groups, trackers, today, scores).size
            if (missing == 0) manager.cancel("group:${group.uuid}:${slot.id}", 0)
            else ReminderNotifications.postGroup(context, group, slot, missing,
                groupNotificationTrackerIds(group, slot, groups, trackers, today, emptyMap()).size)
        } else {
            manager.cancel(trackerId.toInt())
        }
        GridWidget.refresh(context)
    }
}
