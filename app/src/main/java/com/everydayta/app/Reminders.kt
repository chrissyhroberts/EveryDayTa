package com.everydayta.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import android.net.Uri
import android.view.View
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.time.LocalDate
import java.time.ZonedDateTime

object Palettes {
    val names = listOf("Green", "Blue", "Orange", "Purple", "Accessible")
    @Volatile var useAccessibleGlobally: Boolean = false
    val shades = listOf(
        listOf(0xffe0efe0, 0xffb7dcb5, 0xff84c58b, 0xff4b9b67, 0xff176a47),
        listOf(0xffe4f0fa, 0xffb5d8f0, 0xff7db6dd, 0xff4284bd, 0xff20538a),
        listOf(0xfffff0dc, 0xffffd5a1, 0xfff8aa65, 0xffe47c36, 0xffa94a16),
        listOf(0xfff1e8f8, 0xffd8c2ed, 0xffb596d7, 0xff8960b4, 0xff55317e),
        // Viridis, a perceptually ordered, colour-vision-deficiency-friendly scale.
        listOf(0xff440154, 0xff3b528b, 0xff21918c, 0xff5ec962, 0xfffde725)
    )
    fun color(t: Tracker, score: Int): Int = colorFor(t.palette, score, t.labels.size)
    fun colorFor(palette: Int, score: Int, total: Int): Int =
        if (score == 0) 0xffe6e6e6.toInt()
        else shades[(if (useAccessibleGlobally) 4 else palette).coerceIn(0, shades.lastIndex)][((score - 1) * 5 / total.coerceAtLeast(1)).coerceIn(0, 4)].toInt()
}

/** Channel creation and permission status are separated from alarm registration. */
object ReminderNotifications {
    private const val CHANNEL_ID = "daily"
    private const val TEST_ID = 0x45DA
    private const val TEST_ENTRY_TAG = "demo-quick-entry"
    private const val TEST_ENTRY_ID = 0x45DC
    private const val TEST_GROUP_TAG = "demo-group-quick-entry"
    private const val TEST_GROUP_ID = 0x45DD

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily check-ins", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun postTest(context: Context, scheduled: Boolean): Boolean {
        if (!isAvailable(context)) return false
        val content = if (scheduled) "The scheduled alarm reached your phone." else "Notification delivery is working."
        val open = PendingIntent.getActivity(
            context, TEST_ID, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(NotificationManager::class.java).notify(
            TEST_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (scheduled) "EveryDayTa scheduled test" else "EveryDayTa test")
                .setContentText(content)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        return true
    }

    /** Demonstrate the *real* notification scoring controls without storing a tracker or entry.
     * The synthetic tracker ID can never match a SQLite tracker (IDs are positive).
     */
    fun postQuickEntryTest(context: Context, binary: Boolean = false): Boolean {
        if (!isAvailable(context)) return false
        val dummy = if (binary) DemoCheckIn.binaryTracker else DemoCheckIn.numericTracker
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EveryDayTa · Test check-in")
            .setContentText(if (binary) "Tap to choose Done or Not done" else "Tap to choose a score from 0–10")
            .setStyle(Notification.BigTextStyle().bigText(
                "Tap to open the focused check-in, choose one answer, and return to your home screen. " +
                    "This is a demo: nothing is saved."))
            .setContentIntent(openQuick(context, -1L, null, dummy.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        context.getSystemService(NotificationManager::class.java)
            .notify(TEST_ENTRY_TAG, TEST_ENTRY_ID, notification.build())
        return true
    }

    /** A synthetic 10:00 group reminder with three successive scales. Does not
     * schedule any real alarm or write to the Store database. */
    fun postGroupQuickEntryTest(context: Context): Boolean {
        if (!isAvailable(context)) return false
        DemoCheckIn.startGroup(context)
        updateGroupQuickEntryTest(context)
        return true
    }

    /** Repost after answering the current test member. A single notification
     * evolves through the group: binary → Likert → 0–10. */
    fun updateGroupQuickEntryTest(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val state = DemoCheckIn.activeGroupStep(context)
        if (state == null) {
            manager.cancel(TEST_GROUP_TAG, TEST_GROUP_ID)
            return
        }
        val dummy = state.tracker
        val groupIntro = "${state.step + 1} of 3 · ${dummy.name}"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Test 10:00 group check-in")
            .setContentText(groupIntro)
            .setStyle(Notification.BigTextStyle().bigText(
                "$groupIntro\nDemo only — no observations will be saved."))
            .setContentIntent(openGroupDemo(context, state))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        manager.notify(TEST_GROUP_TAG, TEST_GROUP_ID, notification.build())
    }

    private fun openGroupDemo(context: Context, state: DemoCheckIn.GroupStep): PendingIntent {
        val launch = Intent(context, QuickEntryActivity::class.java)
            .setAction("com.everydayta.app.OPEN_QUICK_GROUP_DEMO")
            .setData(Uri.parse("everydayta://demo-group-open/${state.session}/${state.step}"))
            .putExtra(QuickCheckIn.EXTRA_GROUP, -1L)
            .putExtra(QuickCheckIn.EXTRA_TRACKER, state.tracker.id)
            .putExtra(QuickCheckIn.EXTRA_DAY, LocalDate.now().toString())
            .putExtra(QuickCheckIn.EXTRA_DEMO, true)
            .putExtra(DemoCheckIn.EXTRA_GROUP_DEMO, true)
            .putExtra(DemoCheckIn.EXTRA_GROUP_SESSION, state.session)
            .putExtra(DemoCheckIn.EXTRA_GROUP_STEP, state.step)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun dismissQuickEntryTest(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(TEST_ENTRY_TAG, TEST_ENTRY_ID)
    }

    /** Notification tap opens a dedicated one-at-a-time check-in, not MainActivity. */
    private fun openQuick(context: Context, groupId: Long, slotId: String?, trackerId: Long): PendingIntent {
        val key = "${groupId}/${Uri.encode(slotId ?: "individual")}/$trackerId"
        val launch = Intent(context, QuickEntryActivity::class.java)
            .setAction("com.everydayta.app.OPEN_QUICK")
            .setData(Uri.parse("everydayta://quick-open/$key/${LocalDate.now()}"))
            .putExtra(QuickCheckIn.EXTRA_GROUP, groupId)
            .putExtra(QuickCheckIn.EXTRA_SLOT, slotId)
            .putExtra(QuickCheckIn.EXTRA_TRACKER, trackerId)
            .putExtra(QuickCheckIn.EXTRA_DAY, LocalDate.now().toString())
            .putExtra(QuickCheckIn.EXTRA_DEMO, DemoCheckIn.trackerFor(trackerId) != null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // All scoring takes place on QuickEntryActivity's focused screens.
    // No notification reply fields or direct action buttons are created.

    fun postGroup(context: Context, group: TrackerGroup, slot: GroupReminderSlot,
                  missing: Int, dueCount: Int): Boolean {
        if (!isAvailable(context) || missing == 0) return false
        // Resolve the current pending member: the notification itself becomes the mini check-in.
        val state = Store(context).use {
            QuickCheckIn.current(it, group.id, slot.id, -1L, LocalDate.now().toString())
        } ?: return false
        val first = state.pending.firstOrNull() ?: return false
        val remaining = "${state.pending.size} of $dueCount due · ${first.name}"
        val text = slot.message.ifBlank { remaining }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (slot.id == "legacy") group.name else "${group.name} · ${slot.name}")
            .setContentText("${first.name} · ${state.pending.size} left · tap to score")
            .setStyle(Notification.BigTextStyle().bigText(
                if (slot.message.isBlank()) "${first.name} · $remaining"
                else "${first.name}\n$text\n$remaining"))
            .setContentIntent(openQuick(context, group.id, slot.id, -1L))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        context.getSystemService(NotificationManager::class.java)
            .notify("group:${group.uuid}:${slot.id}", 0, notification.build())
        return true
    }

    fun postTracker(context: Context, tracker: Tracker): Boolean {
        if (!isAvailable(context) || tracker.paused) return false
        val state = Store(context).use {
            QuickCheckIn.current(it, -1L, null, tracker.id, LocalDate.now().toString())
        } ?: return false
        val first = state.pending.firstOrNull() ?: return false
        val message = tracker.reminderMessage.ifBlank { "Your scheduled check-in" }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Record ${tracker.name}")
            .setContentText("Tap to record ${tracker.name}")
            .setStyle(Notification.BigTextStyle().bigText(
                if (tracker.reminderMessage.isBlank()) "Tap to record ${tracker.name}"
                else "$message\nTap to record ${tracker.name}"))
            .setContentIntent(openQuick(context, -1L, null, tracker.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        context.getSystemService(NotificationManager::class.java)
            .notify(tracker.id.toInt(), notification.build())
        return true
    }

}

object Scheduler {
    private const val PREFS = "scheduled_reminders"
    const val ACTION_TEST = "com.everydayta.app.ACTION_TEST_REMINDER"
    const val ACTION_GROUP = "com.everydayta.app.ACTION_GROUP_REMINDER"
    const val ACTION_SLOT = "com.everydayta.app.ACTION_GROUP_SLOT_REMINDER"
    private const val TEST_REQUEST_CODE = 0x45DB

    /** Legacy group alarms had no slot identity; remove them during the migration. */
    fun cancelGroup(context: Context, groupId: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, groupId.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_GROUP),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) { alarm.cancel(pi); pi.cancel() }
    }

    private fun slotIntent(context: Context, groupId: Long, slotId: String): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SLOT)
            .setData(Uri.parse("everydayta://group-reminder/$groupId/${Uri.encode(slotId)}"))
            .putExtra("group", groupId).putExtra("slot", slotId)

    fun cancelSlot(context: Context, groupId: Long, slotId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 0, slotIntent(context, groupId, slotId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) { alarm.cancel(pi); pi.cancel() }
    }

    fun cancel(context: Context, id: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarm.cancel(pi)
            pi.cancel()
        }
    }

    /** Reconcile all reminder alarms on data edits, launch, reboot and time-zone change. */
    fun schedule(context: Context) {
        val (active, groups) = Store(context).use { it.trackers() to it.groups() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val groupIds = groups.map { it.id }.toSet()
        prefs.getStringSet("group_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .filter { it !in groupIds }
            .forEach { cancelGroup(context, it) }
        prefs.edit().putStringSet("group_ids", groupIds.map { it.toString() }.toSet()).apply()
        // Cancel removed/edited reminder slots, even if the group itself still exists.
        val currentSlots = groups.flatMap { group ->
            group.effectiveReminderSlots().map { "${group.id}|${it.id}" }
        }.toSet()
        prefs.getStringSet("group_slots", emptySet()).orEmpty().filter { it !in currentSlots }
            .forEach { key ->
                val parts = key.split('|', limit = 2)
                if (parts.size == 2) parts[0].toLongOrNull()?.let { cancelSlot(context, it, parts[1]) }
            }
        prefs.edit().putStringSet("group_slots", currentSlots).apply()
        val slotTags = groups.flatMap { group ->
            group.effectiveReminderSlots().map { "group:${group.uuid}:${it.id}" }
        }.toSet()
        val notifications = context.getSystemService(NotificationManager::class.java)
        prefs.getStringSet("group_slot_tags", emptySet()).orEmpty()
            .filter { it !in slotTags }.forEach { notifications.cancel(it, 0) }
        prefs.edit().putStringSet("group_slot_tags", slotTags).apply()
        // Existing 0.8.x / 0.9.x single-group PendingIntents must not survive an upgrade.
        groups.forEach { group ->
            cancelGroup(context, group.id)
            context.getSystemService(NotificationManager::class.java)
                .cancel("group:${group.uuid}", 0) // old pre-slot notification tag
        }
        val ids = active.map { it.id }.toSet()
        prefs.getStringSet("ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .filter { it !in ids }
            .forEach { cancel(context, it) }
        prefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()

        val now = ZonedDateTime.now()
        val covered = coveredIndividualTrackerIds(groups)
        active.forEach { tracker -> scheduleTracker(context, tracker, now, tracker.id in covered) }
        groups.forEach { group -> scheduleGroup(context, group, now) }
    }

    /** Schedule only the delivered reminder; rescheduling all would cancel other trackers'
     * outstanding alarms, especially when several reminders share the same time. */
    fun scheduleTracker(context: Context, tracker: Tracker, now: ZonedDateTime = ZonedDateTime.now(), suppressed: Boolean? = null) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", tracker.id)
        val pi = PendingIntent.getBroadcast(
            context, tracker.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pi)
        val covered = suppressed ?: Store(context).use { tracker.id in coveredIndividualTrackerIds(it.groups()) }
        // When a tracker joins a group, dismiss any previously posted individual alert.
        // This also applies to paused and reminder-disabled groups: grouping owns the alert.
        if (tracker.paused || covered) {
            context.getSystemService(NotificationManager::class.java).cancel(tracker.id.toInt())
        }
        if (individualReminderAllowed(tracker, covered)) {
            val at = nextDueReminder(now, tracker.hour, tracker.minute, tracker.schedule::dueOn)
            // Inexact alarm; Android may delay it under Doze or battery optimisations.
            if (at != null) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
        }
    }

    fun scheduleGroup(context: Context, group: TrackerGroup,
                      now: ZonedDateTime = ZonedDateTime.now()) {
        group.effectiveReminderSlots().forEach { slot -> scheduleGroupSlot(context, group, slot, now) }
    }

    fun scheduleGroupSlot(context: Context, group: TrackerGroup, slot: GroupReminderSlot,
                          now: ZonedDateTime = ZonedDateTime.now()) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 0,
            slotIntent(context, group.id, slot.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pi)
        if (group.paused || !slot.enabled) {
            context.getSystemService(NotificationManager::class.java)
                .cancel("group:${group.uuid}:${slot.id}", 0)
        }
        if (!group.paused && slot.enabled && slot.trackerIds.isNotEmpty()) {
            val (trackers, groups) = Store(context).use { it.trackers() to it.groups() }
            val at = nextDueReminder(now, slot.hour, slot.minute) { day ->
                groupNotificationTrackerIds(group, slot, groups, trackers, day, emptyMap()).isNotEmpty()
            }
            if (at != null) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                at.toInstant().toEpochMilli(), pi)
        }
    }

    /** Exercise AlarmManager + BroadcastReceiver + notification channel without editing data. */
    fun scheduleTest(context: Context): Boolean = runCatching {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_TEST)
        val pi = PendingIntent.getBroadcast(
            context, TEST_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, pi)
        true
    }.getOrDefault(false)
}

private data class GroupDelivery(val group: TrackerGroup?, val slot: GroupReminderSlot?,
                                 val missing: Int, val total: Int)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                when (intent.action) {
                    QuickCheckIn.ACTION_INLINE -> {
                        // Retired in v0.10.6. Old notification action PendingIntents must
                        // never record scores after the one-screen UX replaces inline actions.
                        // The user can dismiss any pre-upgrade notification and use the next one.
                    }
                    Scheduler.ACTION_TEST -> ReminderNotifications.postTest(context, scheduled = true)
                    Scheduler.ACTION_GROUP, Scheduler.ACTION_SLOT -> {
                        val id = intent.getLongExtra("group", -1L)
                        val slotId = intent.getStringExtra("slot") ?: "legacy"
                        val today = LocalDate.now()
                        val (group, slot, missing, total) = Store(context).use { db ->
                            val allGroups = db.groups()
                            val g = allGroups.firstOrNull { it.id == id }
                            val selectedSlot = g?.effectiveReminderSlots()?.firstOrNull { it.id == slotId }
                            if (g == null || selectedSlot == null) GroupDelivery(null, null, 0, 0)
                            else {
                                val trackers = db.trackers()
                                val scores = db.scoresOn(today)
                                val due = groupNotificationTrackerIds(g, selectedSlot, allGroups,
                                    trackers, today, scores)
                                val ownedDue = groupNotificationTrackerIds(g, selectedSlot, allGroups,
                                    trackers, today, emptyMap())
                                GroupDelivery(g, selectedSlot, due.size, ownedDue.size)
                            }
                        }
                        if (group != null && slot != null) {
                            try {
                                if (!group.paused && slot.enabled)
                                    ReminderNotifications.postGroup(context, group, slot, missing, total)
                            } finally {
                                Scheduler.scheduleGroupSlot(context, group, slot)
                            }
                        }
                    }
                    else -> {
                        val id = intent.getLongExtra("id", -1L)
                        val today = LocalDate.now()
                        val (tracker, covered, dueAndIncomplete) = Store(context).use { db ->
                            val tracker = db.trackers().firstOrNull { it.id == id }
                            val covered = id in coveredIndividualTrackerIds(db.groups())
                            Triple(tracker, covered,
                                tracker != null && dueAndMissing(tracker, today, db.scoresOn(today)))
                        }
                        if (tracker != null) {
                            try {
                                // Re-check ownership and completion at delivery time, even if a
                                // previously scheduled alarm escaped cancellation during editing.
                                if (individualReminderAllowed(tracker, covered) && dueAndIncomplete)
                                    ReminderNotifications.postTracker(context, tracker)
                            } finally {
                                Scheduler.scheduleTracker(context, tracker, suppressed = covered)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // A malformed/stale quick reply must never crash the application process.
                android.util.Log.w("EveryDayTaReminder", "Reminder delivery/action failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                Scheduler.schedule(context)
                GridWidget.refresh(context)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

class GridWidget : android.appwidget.AppWidgetProvider() {
    companion object {
        private const val PERIOD_ACTION = "com.everydayta.app.WIDGET_CHANGE_PERIOD"

        private fun periodIntent(context: Context, widgetId: Int, delta: Int): PendingIntent {
            // The URI is part of Intent equality, so previous/next and different
            // widget instances cannot overwrite one another's PendingIntent.
            val intent = Intent(context, GridWidget::class.java)
                .setAction(PERIOD_ACTION)
                .setData(Uri.parse("everydayta://widget-period/$widgetId/$delta"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra("period_delta", delta)
            return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GridWidget::class.java))
            val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            Palettes.useAccessibleGlobally = context.getSharedPreferences(
                "accessibility", Context.MODE_PRIVATE).getBoolean("accessible_palette", false)
            Store(context).use { store ->
                val trackers = store.trackers()
                val groups = store.groups()
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget)
                    val options = manager.getAppWidgetOptions(id)
                    if (prefs.getString("kind_$id", "tracker") == "group") {
                        val savedUuid = prefs.getString("group_uuid_$id", null)
                        val group = groups.firstOrNull { it.uuid == savedUuid }
                        val month = if (prefs.contains("month_selection_$id"))
                            WidgetPeriodRules.monthAt(prefs.getInt("month_selection_$id", 0))
                        else GroupMonthRules.monthFor(prefs.getInt("month_offset_$id", 0))
                        if (!prefs.contains("month_selection_$id"))
                            prefs.edit().putInt("month_selection_$id", WidgetPeriodRules.monthIndex(month)).apply()
                        val members = group?.trackerIds.orEmpty().mapNotNull { memberId ->
                            trackers.firstOrNull { it.id == memberId }
                        }
                        val observations = store.entriesForMonth(members.map { it.id }, month)
                        views.setTextViewText(R.id.widget_title, group?.name ?: "Group unavailable")
                        views.setTextViewText(R.id.widget_summary,
                            "${month.month.getDisplayName(TextStyle.SHORT, Locale.UK)} ${month.year}")
                        views.setImageViewBitmap(R.id.widget_image,
                            GroupWidgetArtwork.render(context, members, observations, month, options))
                        views.setViewVisibility(R.id.widget_prev, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_next, View.VISIBLE)
                        views.setContentDescription(R.id.widget_prev, "Previous month")
                        views.setContentDescription(R.id.widget_next, "Next month")
                        views.setOnClickPendingIntent(R.id.widget_prev, periodIntent(context, id, -1))
                        views.setOnClickPendingIntent(R.id.widget_next, periodIntent(context, id, 1))
                        val open = Intent(context, MainActivity::class.java)
                            .setAction("com.everydayta.app.WIDGET_OPEN_GROUP_$id")
                            .putExtra("group", group?.id ?: -1L)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        views.setOnClickPendingIntent(R.id.widget_root,
                            PendingIntent.getActivity(context, id, open,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    } else {
                        val savedUuid = prefs.getString("tracker_uuid_$id", null)
                        val tracker = if (savedUuid != null) trackers.firstOrNull { it.uuid == savedUuid }
                            else trackers.firstOrNull { it.id == prefs.getLong("tracker_$id", -1L) }
                        if (savedUuid == null && tracker != null)
                            prefs.edit().putString("tracker_uuid_$id", tracker.uuid).apply()
                        val data = tracker?.let { store.entries(it.id) } ?: emptyMap()
                        val year = if (prefs.contains("year_selection_$id"))
                            prefs.getInt("year_selection_$id", LocalDate.now().year)
                        else WidgetPeriodRules.yearFor(prefs.getInt("year_offset_$id", 0))
                        if (!prefs.contains("year_selection_$id"))
                            prefs.edit().putInt("year_selection_$id", year).apply()
                        views.setTextViewText(R.id.widget_title, tracker?.name ?: "Choose a tracker")
                        views.setTextViewText(R.id.widget_summary, when {
                            tracker == null -> "Tap to configure"
                            tracker.paused -> "$year · paused"
                            else -> "$year · ${data.keys.count { it.year == year }} days"
                        })
                        views.setViewVisibility(R.id.widget_prev, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_next, View.VISIBLE)
                        views.setContentDescription(R.id.widget_prev, "Previous year")
                        views.setContentDescription(R.id.widget_next, "Next year")
                        views.setOnClickPendingIntent(R.id.widget_prev, periodIntent(context, id, -1))
                        views.setOnClickPendingIntent(R.id.widget_next, periodIntent(context, id, 1))
                        views.setImageViewBitmap(R.id.widget_image, WidgetArtwork.render(
                            context, tracker, data, year, prefs.getInt("layout_$id", 0), options))
                        val open = Intent(context, MainActivity::class.java)
                            .setAction("com.everydayta.app.WIDGET_OPEN_TRACKER_$id")
                            .putExtra("tracker", tracker?.id ?: -1L)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        views.setOnClickPendingIntent(R.id.widget_root,
                            PendingIntent.getActivity(context, id, open,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    }
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PERIOD_ACTION) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID)
            val manager = AppWidgetManager.getInstance(context)
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
                widgetId !in manager.getAppWidgetIds(ComponentName(context, GridWidget::class.java))) return
            val delta = intent.getIntExtra("period_delta", 0)
            if (delta != -1 && delta != 1) return
            val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            if (prefs.getString("kind_$widgetId", "tracker") == "group") {
                val selected = if (prefs.contains("month_selection_$widgetId"))
                    WidgetPeriodRules.monthAt(prefs.getInt("month_selection_$widgetId", 0))
                else GroupMonthRules.monthFor(prefs.getInt("month_offset_$widgetId", 0))
                val next = WidgetPeriodRules.boundedMonth(selected, delta)
                prefs.edit().putInt("month_selection_$widgetId",
                    WidgetPeriodRules.monthIndex(next)).apply()
            } else {
                val selected = if (prefs.contains("year_selection_$widgetId"))
                    prefs.getInt("year_selection_$widgetId", LocalDate.now().year)
                else WidgetPeriodRules.yearFor(prefs.getInt("year_offset_$widgetId", 0))
                prefs.edit().putInt("year_selection_$widgetId",
                    WidgetPeriodRules.boundedYear(selected, delta)).apply()
            }
            val pending = goAsync()
            Thread {
                try { refresh(context.applicationContext) } finally { pending.finish() }
            }.start()
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { refresh(context.applicationContext) } finally { pending.finish() } }.start()
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE).edit()
        ids.forEach { id ->
            listOf("kind_", "tracker_", "tracker_uuid_", "group_uuid_", "layout_", "month_offset_", "year_offset_", "year_selection_", "month_selection_")
                .forEach { edit.remove("$it$id") }
        }
        edit.apply()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager,
                                           appWidgetId: Int, newOptions: android.os.Bundle?) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        val pending = goAsync()
        Thread { try { refresh(context.applicationContext) } finally { pending.finish() } }.start()
    }
}
