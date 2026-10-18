package com.everydayta.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(Activity.RESULT_CANCELED)
        val (trackers, groups) = Store(this).use { it.trackers() to it.groups() }
        val prefs = getSharedPreferences("widget_config", MODE_PRIVATE)
        setContent { EveryDayTaTheme {
            var kind by remember { mutableStateOf(prefs.getString("kind_$widgetId", "tracker") ?: "tracker") }
            var trackerId by remember { mutableLongStateOf(
                trackers.firstOrNull { it.uuid == prefs.getString("tracker_uuid_$widgetId", null) }?.id
                    ?: trackers.firstOrNull()?.id ?: -1L) }
            var groupId by remember { mutableLongStateOf(
                groups.firstOrNull { it.uuid == prefs.getString("group_uuid_$widgetId", null) }?.id
                    ?: groups.firstOrNull()?.id ?: -1L) }
            var layout by remember { mutableIntStateOf(prefs.getInt("layout_$widgetId", 0)) }
            Column(Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Configure widget", style = MaterialTheme.typography.headlineSmall)
                Text("What should this widget display?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == "tracker", onClick = { kind = "tracker" })
                    TextButton(onClick = { kind = "tracker" }) { Text("One tracker · whole year") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == "group", onClick = { kind = "group" })
                    TextButton(onClick = { kind = "group" }) { Text("Group · month diary") }
                }
                if (kind == "group") {
                    Text("Choose a group (one row per tracker)",
                        style = MaterialTheme.typography.titleSmall)
                    if (groups.isEmpty()) Text("Create a group in Trackers first.")
                    LazyColumn(Modifier.weight(1f)) {
                        items(groups, key = { it.uuid }) { group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = groupId == group.id,
                                    onClick = { groupId = group.id })
                                TextButton(onClick = { groupId = group.id }) {
                                    Text("${group.name} · ${group.trackerIds.size} trackers")
                                }
                            }
                        }
                    }
                    Text("Use the ‹ and › arrows on the widget to browse months.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Choose a tracker", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.weight(1f)) {
                        items(trackers, key = { it.uuid }) { tracker ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = trackerId == tracker.id,
                                    onClick = { trackerId = tracker.id })
                                TextButton(onClick = { trackerId = tracker.id }) { Text(tracker.name) }
                            }
                        }
                    }
                    Text("Use ‹ and › on the widget to browse years.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Year view")
                    listOf("Week-aligned", "Continuous block", "12-month calendar")
                        .forEachIndexed { index, name ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = layout == index, onClick = { layout = index })
                                TextButton(onClick = { layout = index }) { Text(name) }
                            }
                        }
                }
                Button(
                    enabled = if (kind == "group") groupId >= 0 else trackerId >= 0,
                    onClick = {
                        val tracker = trackers.firstOrNull { it.id == trackerId }
                        val group = groups.firstOrNull { it.id == groupId }
                        prefs.edit()
                            .putString("kind_$widgetId", kind)
                            .putString("tracker_uuid_$widgetId", tracker?.uuid)
                            .putLong("tracker_$widgetId", trackerId)
                            .putString("group_uuid_$widgetId", group?.uuid)
                            .putInt("layout_$widgetId", layout)
                            .putInt("month_offset_$widgetId", 0)
                            .putInt("year_offset_$widgetId", 0)
                            .putInt("year_selection_$widgetId", java.time.LocalDate.now().year)
                            .putInt("month_selection_$widgetId",
                                WidgetPeriodRules.monthIndex(java.time.YearMonth.now()))
                            .apply()
                        Thread { GridWidget.refresh(applicationContext) }.start()
                        setResult(Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }, modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Add widget") }
            }
        } }
    }
}
