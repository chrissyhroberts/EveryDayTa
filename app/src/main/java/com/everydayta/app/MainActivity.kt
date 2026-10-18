package com.everydayta.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.provider.Settings
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var requestedTracker by mutableLongStateOf(-1L)
    private var requestedGroup by mutableLongStateOf(-1L)
    private var requestedGroupSlot by mutableStateOf<String?>(null)
    private var intentRevision by mutableIntStateOf(0)
    var notificationRevision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        notificationRevision++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTracker = intent.getLongExtra("tracker", -1L)
        requestedGroup = intent.getLongExtra("group", -1L)
        requestedGroupSlot = intent.getStringExtra("slot")
        intentRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 enforces edge-to-edge for apps targeting API 35. Compose
        // owns the insets; system-bar icons stay legible against the pale canvas.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        requestedTracker = intent.getLongExtra("tracker", -1L)
        requestedGroup = intent.getLongExtra("group", -1L)
        requestedGroupSlot = intent.getStringExtra("slot")
        ReminderNotifications.ensureChannel(this)
        lifecycleScope.launch(Dispatchers.IO) { Scheduler.schedule(this@MainActivity) }
        setContent { EveryDayTaTheme { App(this, requestedTracker, requestedGroup, requestedGroupSlot, intentRevision) } }
    }
}

@Composable
fun App(activity: MainActivity, initial: Long, initialGroup: Long, initialSlot: String?, intentRevision: Int) {
    val store = remember { Store(activity) }
    var revision by remember { mutableIntStateOf(0) }
    val trackers by produceState(initialValue = emptyList<Tracker>(), revision) {
        value = withContext(Dispatchers.IO) { store.trackers() }
    }
    val groups by produceState(initialValue = emptyList<TrackerGroup>(), revision) {
        value = withContext(Dispatchers.IO) { store.groups() }
    }
    var status by remember { mutableStateOf("") }
    fun shareTracker(tracker: Tracker, year: Int, layout: Int, month: Int, image: Boolean, showDates: Boolean = true) {
        activity.lifecycleScope.launch {
            try {
                val fileUri = withContext(Dispatchers.IO) {
                    Store(activity).use { database ->
                        val entries = database.entries(tracker.id)
                        if (image) ShareExports.image(activity, tracker, entries, year, layout, month, showDates)
                        else ShareExports.dataset(activity, tracker, entries, year)
                    }
                }
                val mime = if (image) "image/png" else "text/csv"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    clipData = ClipData.newUri(activity.contentResolver, "EveryDayTa export", fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(send,
                    if (image) "Share calendar image" else "Share tracker CSV"))
            } catch (e: Exception) {
                status = "Share failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
    var selected by remember { mutableLongStateOf(if (initialGroup >= 0) -1L else initial) }
    var groupSelected by remember { mutableLongStateOf(initialGroup) }
    var selectedGroupSlot by remember { mutableStateOf(if (initialGroup >= 0) initialSlot else null) }
    LaunchedEffect(initial, initialGroup, initialSlot, intentRevision) {
        when {
            initialGroup >= 0 -> { selected = -1L; groupSelected = initialGroup; selectedGroupSlot = initialSlot }
            initial >= 0 -> { selected = initial; groupSelected = -1L; selectedGroupSlot = null }
        }
    }
    val permissionRevision = activity.notificationRevision
    val notificationsAvailable = remember(permissionRevision) { ReminderNotifications.isAvailable(activity) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        activity.notificationRevision++
        status = if (allowed && ReminderNotifications.isAvailable(activity))
            "Notifications enabled" else "Notifications disabled — check Android settings"
    }
    fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            activity.startActivity(settingsIntent)
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Tracker?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TrackerGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var pendingBulkDelete by remember { mutableStateOf<Set<Long>?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val writeMutex = remember { Mutex() }
    var pendingCsv by remember { mutableStateOf<Pair<String, CsvPreview>?>(null) }
    BackHandler(enabled = creating || editing != null || creatingGroup || editingGroup != null ||
        selected >= 0 || groupSelected >= 0 || tab != 0) {
        when {
            creating || editing != null -> { creating = false; editing = null }
            creatingGroup || editingGroup != null -> { creatingGroup = false; editingGroup = null }
            groupSelected >= 0 -> { groupSelected = -1L; selectedGroupSlot = null }
            selected >= 0 -> selected = -1L
            else -> tab = 0
        }
    }
    val canEditLabels by produceState(initialValue = editing == null, editing?.id, revision) {
        value = withContext(Dispatchers.IO) {
            editing?.let { store.entries(it.id).isEmpty() } ?: true
        }
    }

    // Keep action as the final lambda parameter so `runIo("label") { ... }`
    // binds to the suspending IO action, not to the optional completion callback.
    fun runIo(label: String, onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = "$label…"
        activity.lifecycleScope.launch {
            try {
                writeMutex.withLock {
                    withContext(Dispatchers.IO) {
                        action()
                        Scheduler.schedule(activity)
                        GridWidget.refresh(activity)
                    }
                }
                status = "$label completed"
                revision++
                onSuccess()
            } catch (e: Exception) {
                status = "$label failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }
    // Quick check-ins are queued and written in tap order. Unlike backup/restore
    // operations, a score tap must never be discarded because another is saving.
    fun recordScore(id: Long, day: LocalDate, value: Int?) {
        if (busy) {
            status = "Please wait for the current data operation to finish"
            return
        }
        activity.lifecycleScope.launch {
            try {
                writeMutex.withLock {
                    withContext(Dispatchers.IO) {
                        store.score(id, day, value)
                        GridWidget.refresh(activity)
                    }
                }
                revision++
            } catch (e: Exception) {
                status = "Could not record score: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
    LaunchedEffect(status, busy) {
        if (!busy && status.isNotBlank()) {
            delay(5000)
            status = ""
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runIo("JSON backup") { writeUri(activity, uri, store.backup()) }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runIo("CSV export") { writeUri(activity, uri, store.csv()) }
    }
    val csvImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runIo("CSV validation") {
            val content = readUri(activity, uri)
            val preview = store.previewCsv(content)
            withContext(Dispatchers.Main) { pendingCsv = content to preview }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runIo("Backup validation") {
            val content = readUri(activity, uri)
            store.validateBackup(content)
            withContext(Dispatchers.Main) { pendingRestore = content }
        }
    }

    if (pendingCsv != null) {
        AlertDialog(
            onDismissRequest = { pendingCsv = null },
            title = { Text("Import observations?") },
            text = {
                val p = pendingCsv!!.second
                Text("${p.newTrackers} new trackers · ${p.inserted} new entries · " +
                    "${p.changed} overwritten · ${p.unchanged} unchanged. " +
                    (if (p.outsideDatePolicy > 0)
                        "${p.outsideDatePolicy} rows fall outside manual date entry permissions; importing them is still allowed. "
                     else "") +
                    "CSV doesn't include unused score levels, reminders or palettes.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val content = pendingCsv!!.first
                    pendingCsv = null
                    runIo("CSV import") { store.importCsv(content) }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingCsv = null }) { Text("Cancel") } }
        )
    }
    if (pendingBulkDelete != null) {
        val selectedTrackers = trackers.filter { it.id in pendingBulkDelete.orEmpty() }
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = null },
            title = { Text("Delete ${selectedTrackers.size} trackers permanently?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All recorded scores for these trackers will be deleted. They will also be removed from their groups. This cannot be undone.")
                    Text(selectedTrackers.joinToString(" · ") { it.name }, fontWeight = FontWeight.Bold)
                    Text("Export a JSON backup first if you might need these records again.")
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && selectedTrackers.isNotEmpty(), onClick = {
                    val ids = pendingBulkDelete.orEmpty()
                    pendingBulkDelete = null
                    runIo("Delete ${ids.size} trackers") { store.deleteTrackers(ids); Unit }
                }) { Text("Delete permanently", color = DayStyle.error) }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = null }) { Text("Cancel") } }
        )
    }
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all your data?") },
            text = { Text("Restore replaces every tracker and its history. Export a backup first. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val content = pendingRestore!!
                    pendingRestore = null
                    selected = -1L
                    groupSelected = -1L
                    runIo("JSON restore") { store.restore(content) }
                }) { Text("Replace data") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().background(DayStyle.canvas).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painter = painterResource(R.drawable.icon_header), contentDescription = null,
                modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(9.dp))
            Text("EveryDayTa", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DayStyle.ink, maxLines = 1)
            if (selected >= 0 || groupSelected >= 0 || creating || editing != null ||
                creatingGroup || editingGroup != null || tab != 0) {
                TextButton(onClick = {
                    selected = -1L
                    groupSelected = -1L
                    selectedGroupSlot = null
                    creating = false
                    editing = null
                    creatingGroup = false
                    editingGroup = null
                    tab = 0
                }) { Text("Home") }
            }
            if (!creating && editing == null && !creatingGroup && editingGroup == null) {
                FilledTonalButton(
                    onClick = { selected = -1L; groupSelected = -1L; editing = null; creating = true },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) { Text("+ New") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
        if (status.isNotBlank() && !busy && status != "Update entry completed") {
            Surface(color = if (status.contains("failed")) Color(0xFFFDECEA) else DayStyle.paleTeal,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(10.dp)) {
                Text(status, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall,
                    color = if (status.contains("failed")) DayStyle.error else DayStyle.teal)
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                creatingGroup || editingGroup != null -> GroupEditor(
                    existing = editingGroup,
                    trackers = trackers,
                    onCancel = { creatingGroup = false; editingGroup = null },
                    onSave = { group ->
                        runIo("Save group", onSuccess = {
                            if (group.effectiveReminderSlots().any { it.enabled } && !notificationsAvailable) requestNotificationAccess()
                            creatingGroup = false
                            editingGroup = null
                        }) { store.saveGroup(group); Unit }
                    },
                    onDelete = { id ->
                        groupSelected = -1L
                        creatingGroup = false
                        editingGroup = null
                        runIo("Delete group") { store.deleteGroup(id) }
                    }
                )
                creating || editing != null -> TrackerEditor(
                    existing = editing,
                    canEditLabels = canEditLabels,
                    onCancel = { creating = false; editing = null },
                    existingNames = trackers.map { it.id to it.name },
                    onSave = { tracker ->
                        runIo("Save tracker", action = { store.save(tracker); Unit }, onSuccess = {
                            if (tracker.reminder && Build.VERSION.SDK_INT >= 33 &&
                                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                requestNotificationAccess()
                            }
                            if (creating) { selected = -1L; tab = 0 }
                            creating = false
                            editing = null
                        })
                    }
                )
                groupSelected >= 0 && groups.any { it.id == groupSelected } -> {
                    val group = groups.first { it.id == groupSelected }
                    val reminderSlot = group.effectiveReminderSlots().firstOrNull { it.id == selectedGroupSlot }
                    val visibleMemberIds = reminderSlot?.trackerIds ?: group.trackerIds
                    val members = visibleMemberIds.mapNotNull { memberId ->
                        trackers.firstOrNull { it.id == memberId && !it.paused }
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (group.paused) "GROUP PAUSED" else "GROUP CHECK-IN", modifier = Modifier.weight(1f),
                                color = DayStyle.subdued, style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { runIo(if (group.paused) "Resume group" else "Pause group") {
                                store.saveGroup(group.copy(paused = !group.paused)); Unit
                            } }) { Text(if (group.paused) "Resume" else "Pause") }
                            if (reminderSlot != null) {
                                TextButton(onClick = { selectedGroupSlot = null }) { Text("All") }
                            }
                            TextButton(onClick = { editingGroup = group }) { Text("Edit") }
                        }
                        if (group.paused) {
                            Text("This group is paused. Its alerts are off and individual reminders stay suppressed; other groups are unaffected.",
                                modifier = Modifier.padding(18.dp), color = DayStyle.subdued)
                        } else TodayScreen(members, store, revision, notificationsAvailable,
                            onEnableNotifications = ::requestNotificationAccess,
                            onScore = ::recordScore,
                            onOpen = { groupSelected = -1L; selected = it },
                            onCreate = { creating = true },
                            title = if (reminderSlot != null) "${group.name} · ${reminderSlot.name}" else group.name,
                            showCreate = false)
                    }
                }
                selected >= 0 && trackers.any { it.id == selected } -> {
                    val t = trackers.first { it.id == selected }
                    TrackerPage(t, store, revision,
                        onShareImage = { year, layout, month, showDates -> shareTracker(t, year, layout, month, true, showDates) },
                        onShareCsv = { year -> shareTracker(t, year, 0, 0, false) },
                        onScore = ::recordScore,
                        onEdit = { editing = t },
                        onTogglePause = { runIo(if (t.paused) "Resume tracker" else "Pause tracker") {
                            store.save(t.copy(paused = !t.paused)); Unit
                        } },
                        onDelete = { selected = -1L; runIo("Delete tracker") { store.delete(t.id) } })
                }
                else -> when (tab) {
                    0 -> TodayScreen(trackers, store, revision,
                        notificationsAvailable = notificationsAvailable,
                        groups = groups,
                        onOpenGroup = { selected = -1L; groupSelected = it; selectedGroupSlot = null },
                        onEnableNotifications = ::requestNotificationAccess,
                        onScore = ::recordScore,
                        onOpen = { selected = it }, onCreate = { creating = true })
                    1 -> TrackerListScreen(trackers, groups,
                        onOpen = { groupSelected = -1L; selected = it },
                        onEdit = { tracker -> selected = -1L; groupSelected = -1L; editing = tracker },
                        onBulkDelete = { ids -> pendingBulkDelete = ids },
                        onCreate = { creating = true },
                        onOpenGroup = { selected = -1L; groupSelected = it; selectedGroupSlot = null },
                        onNewGroup = { selected = -1L; groupSelected = -1L; creatingGroup = true },
                        onToggleGroupPause = { group -> runIo(if (group.paused) "Resume group" else "Pause group") {
                            store.saveGroup(group.copy(paused = !group.paused)); Unit
                        } },
                        onTogglePause = { tracker -> runIo(if (tracker.paused) "Resume tracker" else "Pause tracker") {
                            store.save(tracker.copy(paused = !tracker.paused)); Unit
                        } })
                    else -> DataScreen(
                        notificationsAvailable = notificationsAvailable,
                        remindersConfigured = trackers.any { it.reminder } || groups.any { g -> g.effectiveReminderSlots().any { it.enabled } },
                        onEnableNotifications = ::requestNotificationAccess,
                        onNotificationSettings = {
                            activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                        },
                        onTestNow = {
                            status = if (ReminderNotifications.postTest(activity, scheduled = false))
                                "Test notification sent" else "Notifications are blocked — check Android settings"
                        },
                        onTestDataEntry = {
                            status = if (ReminderNotifications.postQuickEntryTest(activity))
                                "Test score notification sent · no data will be saved"
                            else "Notifications are blocked — check Android settings"
                        },
                        onTestBinaryEntry = {
                            status = if (ReminderNotifications.postQuickEntryTest(activity, binary = true))
                                "Test Done / Not done check-in sent · no data saved"
                            else "Notifications are blocked — check Android settings"
                        },
                        onTestGroupEntry = {
                            status = if (ReminderNotifications.postGroupQuickEntryTest(activity))
                                "Three-step group test sent · no records will be saved"
                            else "Notifications are blocked — check Android settings"
                        },
                        onTestScheduled = {
                            status = if (Scheduler.scheduleTest(activity))
                                "Test alarm set for about one minute from now (Android may delay it)"
                            else "Couldn't schedule a test alarm"
                        },
                        onBackup = { export.launch("everydayta-backup.json") },
                        onExportCsv = { csv.launch("everydayta.csv") },
                        onImportBackup = { import.launch(arrayOf("application/json", "*/*")) },
                        onImportCsv = { csvImport.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }
                    )
                }
            }
        }
        if (!creating && editing == null && !creatingGroup && editingGroup == null) {
            Row(Modifier.fillMaxWidth().background(DayStyle.surface)
                .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "Trackers", "Settings").forEachIndexed { i, title ->
                    val active = selected < 0 && groupSelected < 0 && tab == i
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp).clickable {
                            tab = i; selected = -1L; groupSelected = -1L
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) DayStyle.paleTeal else DayStyle.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(title, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) DayStyle.teal else DayStyle.subdued)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    trackers: List<Tracker>, store: Store, revision: Int,
    notificationsAvailable: Boolean,
    onEnableNotifications: () -> Unit,
    onScore: (Long, LocalDate, Int?) -> Unit,
    onOpen: (Long) -> Unit, onCreate: () -> Unit,
    groups: List<TrackerGroup> = emptyList(),
    onOpenGroup: (Long) -> Unit = {},
    title: String = "Today", showCreate: Boolean = true
) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
        }
    }
    val values by produceState(initialValue = emptyMap<Long, Int?>(), trackers, revision, today) {
        value = withContext(Dispatchers.IO) { store.scoresOn(today) }
    }
    var showNotDue by remember(title) { mutableStateOf(false) }
    val activeTrackers = trackers.filterNot { it.paused }
    val dueTrackers = activeTrackers.filter { it.schedule.dueOn(today) }
    val visibleTrackers = if (showNotDue) activeTrackers else dueTrackers
    val completed = dueTrackers.count { values[it.id] != null }
    var openQuickScore by remember { mutableStateOf<Long?>(null) }
    var pendingTodayClear by remember { mutableStateOf<Long?>(null) }
    val editingTracker = activeTrackers.firstOrNull { it.id == openQuickScore }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold), color = DayStyle.ink)
                    Text("${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.UK)} ${today.year}",
                        style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                }
                if (trackers.isNotEmpty()) {
                    Text("$completed / ${dueTrackers.size} due",
                        style = MaterialTheme.typography.labelMedium,
                        color = DayStyle.teal,
                        modifier = Modifier.background(DayStyle.paleTeal, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        if (groups.any { !it.paused }) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(groups.filterNot { it.paused }, key = { it.id }) { group ->
                        val members = activeTrackers.filter { it.id in group.trackerIds && it.schedule.dueOn(today) }
                        val missing = members.count { values[it.id] == null }
                        FilterChip(
                            selected = false,
                            onClick = { onOpenGroup(group.id) },
                            label = { Text("${group.name} · ${members.size - missing}/${members.size}") }
                        )
                    }
                }
            }
        }
        if ((trackers.any { it.reminder } || groups.any { it.reminder }) && !notificationsAvailable) {
            item {
                Surface(color = Color(0xFFFFF0DE), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onEnableNotifications)) {
                    Text("Reminders are blocked · Enable notifications  ›",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodySmall, color = DayStyle.ink)
                }
            }
        }
        if (activeTrackers.isNotEmpty() && dueTrackers.size < activeTrackers.size) {
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${dueTrackers.size} scheduled today · ${activeTrackers.size - dueTrackers.size} not due",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = DayStyle.subdued)
                    TextButton(onClick = { showNotDue = !showNotDue }) {
                        Text(if (showNotDue) "Only due" else "Show all")
                    }
                }
            }
        }
        if (visibleTrackers.isEmpty() && activeTrackers.isNotEmpty()) {
            item { Text("Nothing scheduled today. You can still view and enter other trackers using Show all.",
                color = DayStyle.subdued, style = MaterialTheme.typography.bodyMedium) }
        }
        if (trackers.isEmpty() && showCreate) {
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Start tracking anything", style = MaterialTheme.typography.titleMedium)
                        Text("Create a tracker, then tap its row each day to record a score.",
                            style = MaterialTheme.typography.bodyMedium, color = DayStyle.subdued)
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                            Text("Create your first tracker")
                        }
                    }
                }
            }
        } else {
            items(visibleTrackers, key = { it.id }) { t ->
                val score = values[t.id]
                val label = score?.let { t.labels.getOrNull(it - 1) } ?: "Tap to record"
                Surface(
                    color = DayStyle.surface,
                    border = BorderStroke(1.dp, DayStyle.outline),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                        .clickable { openQuickScore = t.id }
                        .semantics {
                            contentDescription = "${t.name}, ${score?.let { "recorded: $label" } ?: "no entry"}. Tap to choose score."
                        }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(width = 5.dp, height = 35.dp)
                            .background(Color(Palettes.color(t, score ?: 0)), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold, color = DayStyle.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge)
                            Text("${t.labels.size} choices", style = MaterialTheme.typography.labelSmall,
                                color = DayStyle.subdued)
                        }
                        Surface(color = if (score == null) DayStyle.canvas else
                                Color(Palettes.color(t, score)),
                            shape = RoundedCornerShape(10.dp)) {
                            Text(label, Modifier.widthIn(max = 132.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (score != null && Color(Palettes.color(t, score)).luminance() < 0.40f)
                                    Color.White else DayStyle.ink)
                        }
                        if (score != null) {
                            IconButton(
                                onClick = { pendingTodayClear = t.id },
                                modifier = Modifier.size(44.dp).semantics {
                                    contentDescription = "Clear today's score for ${t.name}"
                                }
                            ) {
                                Text("×", fontSize = 24.sp, color = DayStyle.subdued)
                            }
                        }
                        Text("›", fontSize = 23.sp, color = DayStyle.teal)
                    }
                }
            }
        }
    }

    val clearTracker = trackers.firstOrNull { it.id == pendingTodayClear }
    if (clearTracker != null) {
        AlertDialog(
            onDismissRequest = { pendingTodayClear = null },
            title = { Text("Clear today's score?") },
            text = { Text("Remove today's entry for ${clearTracker.name}? Earlier days will not be changed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingTodayClear = null
                    openQuickScore = null
                    onScore(clearTracker.id, today, null)
                }) { Text("Clear score") }
            },
            dismissButton = { TextButton(onClick = { pendingTodayClear = null }) { Text("Cancel") } }
        )
    }

    if (editingTracker != null) {
        val current = values[editingTracker.id]
        ModalBottomSheet(onDismissRequest = { openQuickScore = null },
            containerColor = DayStyle.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(editingTracker.name,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.UK)} · " +
                            (current?.let { "${editingTracker.labels[it - 1]} recorded" } ?: "Not recorded"),
                            style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                    }
                    if (current != null) {
                        TextButton(onClick = {
                            openQuickScore = null
                            pendingTodayClear = editingTracker.id
                        }) { Text("Clear today") }
                    }
                    TextButton(onClick = { openQuickScore = null; onOpen(editingTracker.id) }) {
                        Text("View year ›")
                    }
                }
                Text("CHOOSE TODAY'S SCORE", style = MaterialTheme.typography.labelSmall,
                    color = DayStyle.subdued)
                // A two-column grid fits 11-point scales without requiring a horizontal swipe.
                editingTracker.labels.chunked(2).forEachIndexed { row, labels ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        labels.forEachIndexed { index, label ->
                            val value = row * 2 + index + 1
                            val selected = value == current
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .clickable {
                                        openQuickScore = null
                                        onScore(editingTracker.id, today, value)
                                    }
                                    .semantics {
                                        contentDescription = "Record $label for ${editingTracker.name}"
                                    },
                                color = if (selected) DayStyle.paleTeal else DayStyle.canvas,
                                border = BorderStroke(if (selected) 2.dp else 1.dp,
                                    if (selected) DayStyle.teal else DayStyle.outline),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(16.dp).background(
                                        Color(Palettes.color(editingTracker, value)), RoundedCornerShape(5.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                                        color = DayStyle.ink)
                                }
                            }
                        }
                        if (labels.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TrackerListScreen(
    trackers: List<Tracker>, groups: List<TrackerGroup>,
    onOpen: (Long) -> Unit, onCreate: () -> Unit,
    onEdit: (Tracker) -> Unit, onBulkDelete: (Set<Long>) -> Unit,
    onOpenGroup: (Long) -> Unit, onNewGroup: () -> Unit,
    onToggleGroupPause: (TrackerGroup) -> Unit, onTogglePause: (Tracker) -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    // A completed delete or restore can invalidate an old selection.
    LaunchedEffect(trackers) { selectedIds.removeAll { id -> trackers.none { it.id == id } } }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your trackers", style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold))
                    Text("Your own little collection of patterns", color = DayStyle.subdued)
                }
                Button(onClick = onCreate, shape = RoundedCornerShape(13.dp)) { Text("+ New") }
            }
        }
        if (trackers.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectionMode) {
                        Text("${selectedIds.size} selected", modifier = Modifier.weight(1f),
                            color = DayStyle.subdued)
                        TextButton(onClick = {
                            if (selectedIds.size == trackers.size) selectedIds.clear()
                            else { selectedIds.clear(); selectedIds.addAll(trackers.map { it.id }) }
                        }) { Text(if (selectedIds.size == trackers.size) "Clear selection" else "Select all") }
                        TextButton(enabled = selectedIds.isNotEmpty(), onClick = {
                            onBulkDelete(selectedIds.toSet())
                            selectedIds.clear()
                            selectionMode = false
                        }) { Text("Delete", color = DayStyle.error) }
                        TextButton(onClick = { selectionMode = false; selectedIds.clear() }) { Text("Cancel") }
                    } else {
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { selectionMode = true }) { Text("Select / delete") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check-in groups", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Several trackers, one reminder", style = MaterialTheme.typography.bodySmall,
                            color = DayStyle.subdued)
                    }
                    OutlinedButton(onClick = onNewGroup) { Text("+ Group") }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text("Make a group for a morning, evening or weekly routine.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(groups.filterNot { it.paused }, key = { "group_${it.id}" }) { group ->
                DayPanel(Modifier.fillMaxWidth().clickable { onOpenGroup(group.id) }) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(37.dp).background(DayStyle.paleTeal,
                            RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("${group.trackerIds.size}", fontWeight = FontWeight.Bold, color = DayStyle.teal)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold, color = DayStyle.ink)
                            Text("${group.trackerIds.size} trackers  ·  " +
                                (if (group.multipleReminders)
                                    "${group.reminderSlots.count { it.enabled }} reminder times"
                                else if (group.reminder) "%02d:%02d reminder".format(group.hour, group.minute)
                                else "No reminder"),
                                color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onToggleGroupPause(group) }) { Text("Pause") }
                        Text("›", fontSize = 24.sp, color = DayStyle.teal)
                    }
                }
            }
            if (groups.any { it.paused }) {
                item { Text("Paused groups", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = DayStyle.subdued) }
                items(groups.filter { it.paused }, key = { "paused_group_${it.id}" }) { group ->
                    DayPanel(Modifier.fillMaxWidth().clickable { onOpenGroup(group.id) }) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, fontWeight = FontWeight.SemiBold, color = DayStyle.subdued)
                                Text("Group reminders off · members unchanged",
                                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                            }
                            TextButton(onClick = { onToggleGroupPause(group) }) { Text("Resume") }
                        }
                    }
                }
            }
            item {
                Text("Individual trackers", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        if (trackers.isEmpty()) {
            item { Text("No trackers yet. Tap + New to get started.", color = DayStyle.subdued) }
        }
        items(trackers.filterNot { it.paused }, key = { it.id }) { t ->
            DayPanel(Modifier.fillMaxWidth().clickable {
                if (selectionMode) {
                    if (t.id in selectedIds) selectedIds.remove(t.id) else selectedIds.add(t.id)
                } else onOpen(t.id)
            }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) Checkbox(checked = t.id in selectedIds, onCheckedChange = { checked ->
                        if (checked && t.id !in selectedIds) selectedIds.add(t.id)
                        if (!checked) selectedIds.remove(t.id)
                    }) else Box(Modifier.size(42.dp).background(Color(Palettes.color(t, t.labels.size)),
                        RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("${t.labels.size} levels  ·  ${t.schedule.description()}  ·  " +
                            (if (t.reminder) "%02d:%02d reminder".format(t.hour, t.minute)
                            else "No reminder"), color = DayStyle.subdued,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!selectionMode) {
                        TextButton(onClick = { onEdit(t) }) { Text("Edit") }
                        TextButton(onClick = { onTogglePause(t) }) { Text("Pause") }
                    }
                }
            }
        }
        if (trackers.any { it.paused }) {
            item { Text("Paused", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = DayStyle.subdued) }
            items(trackers.filter { it.paused }, key = { "paused_${it.id}" }) { t ->
                DayPanel(Modifier.fillMaxWidth().clickable {
                    if (selectionMode) {
                        if (t.id in selectedIds) selectedIds.remove(t.id) else selectedIds.add(t.id)
                    } else onOpen(t.id)
                }) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selectionMode) Checkbox(checked = t.id in selectedIds, onCheckedChange = { checked ->
                            if (checked && t.id !in selectedIds) selectedIds.add(t.id)
                            if (!checked) selectedIds.remove(t.id)
                        }) else Box(Modifier.size(34.dp).background(DayStyle.outline, RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold, color = DayStyle.subdued)
                            Text("History kept · no check-ins or reminders", style = MaterialTheme.typography.bodySmall,
                                color = DayStyle.subdued)
                        }
                        if (!selectionMode) {
                            TextButton(onClick = { onEdit(t) }) { Text("Edit") }
                            TextButton(onClick = { onTogglePause(t) }) { Text("Resume") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataScreen(
    notificationsAvailable: Boolean, remindersConfigured: Boolean,
    onEnableNotifications: () -> Unit, onNotificationSettings: () -> Unit,
    onTestNow: () -> Unit, onTestScheduled: () -> Unit,
    onTestDataEntry: () -> Unit, onTestBinaryEntry: () -> Unit,
    onTestGroupEntry: () -> Unit,
    onBackup: () -> Unit, onExportCsv: () -> Unit,
    onImportBackup: () -> Unit, onImportCsv: () -> Unit
) {
    val accessibility = LocalAccessibilityPreferences.current
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text("Stored privately on this device. You choose what leaves it.",
                color = DayStyle.subdued)
        }
        item {
        DayPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Accessibility", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("Text size · ${(accessibility.textScale * 100).toInt()}%",
                    color = DayStyle.ink, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = accessibility.textScale,
                    onValueChange = { accessibility.updateTextScale(it) },
                    valueRange = .85f..1.6f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Applies throughout EveryDayTa and adds to your Android text-size setting.",
                    color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Colourblind-friendly colours", fontWeight = FontWeight.SemiBold)
                        Text("Use a colour-vision-friendly scale across trackers and widgets. " +
                            "Original palettes stay saved.",
                            style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                    }
                    Switch(
                        checked = accessibility.preferAccessiblePalette,
                        onCheckedChange = { accessibility.setAccessiblePalette(it) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Palettes.shades.last().forEach { shade ->
                        Box(Modifier.weight(1f).height(16.dp).background(Color(shade.toInt()),
                            RoundedCornerShape(4.dp)))
                    }
                }
                Text("Colours are also accompanied by score labels in entry screens.",
                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
            }
        }
    }
        item {
            DayPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reminders & notifications", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (notificationsAvailable) "Notifications are enabled on this device."
                        else "Notifications are disabled or the reminder channel is blocked.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodyMedium)
                    Text(if (remindersConfigured) "Enabled tracker and group reminders are scheduled around their selected times."
                        else "No reminders are enabled yet.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                    if (!notificationsAvailable) {
                        Button(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable notifications")
                        }
                    }
                    OutlinedButton(onClick = onTestNow, enabled = notificationsAvailable,
                        modifier = Modifier.fillMaxWidth()) { Text("Send test notification now") }
                    OutlinedButton(onClick = onTestDataEntry, enabled = notificationsAvailable,
                        modifier = Modifier.fillMaxWidth()) { Text("Test score check-in (0–10)") }
                    OutlinedButton(onClick = onTestBinaryEntry, enabled = notificationsAvailable,
                        modifier = Modifier.fillMaxWidth()) { Text("Test Done / Not done check-in") }
                    OutlinedButton(onClick = onTestGroupEntry, enabled = notificationsAvailable,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Test 10:00 group · binary → Likert → 0–10")
                    }
                    Text("The group test starts immediately, as if its 10:00 reminder had fired. " +
                        "It advances through three dummy trackers and never saves observations.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                    Text("Both single-tracker tests open the same focused score screen. Choose one answer to close it. No data is saved.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onTestScheduled, enabled = notificationsAvailable,
                        modifier = Modifier.fillMaxWidth()) { Text("Test scheduled reminder (1 minute)") }
                    TextButton(onClick = onNotificationSettings) { Text("Android notification settings ›") }
                    Text("Alarm delivery can be delayed by Android battery management. " +
                        "A test notification proves delivery; the scheduled test checks the alarm path.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            DayPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Keep everything safe", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("A JSON backup includes trackers, groups, colours, scores, entries and reminders.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onBackup, modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)) { Text("Save complete backup") }
                    OutlinedButton(onClick = onImportBackup,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)) { Text("Restore from backup") }
                    Text("Restoring replaces all current data. You'll be asked to confirm.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            DayPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Work with your observations", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("CSV is useful for spreadsheets. It contains recorded scores, not complete tracker settings.",
                        color = DayStyle.subdued, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onExportCsv,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)) { Text("Export scores as CSV") }
                    OutlinedButton(onClick = onImportCsv,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)) { Text("Import scores from CSV") }
                }
            }
        }
    }
}

/** Tracker-specific detail and its three yearly visualisations. */
@Composable
fun TrackerPage(
    t: Tracker,
    store: Store,
    revision: Int,
    onShareImage: (Int, Int, Int, Boolean) -> Unit,
    onShareCsv: (Int) -> Unit,
    onScore: (Long, LocalDate, Int?) -> Unit,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    var year by remember(t.id) { mutableIntStateOf(LocalDate.now().year) }
    var layout by remember(t.id) { mutableIntStateOf(2) }
    var numbers by remember(t.id) { mutableStateOf(true) }
    var chosen by remember(t.id) { mutableStateOf(LocalDate.now()) }
    var monthDrill by remember(t.id) { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = layout == 2 && monthDrill != 0) { monthDrill = 0 }
    val data by produceState(initialValue = emptyMap<LocalDate, Int>(), t.id, revision) {
        value = withContext(Dispatchers.IO) { store.entries(t.id) }
    }
    fun openDay(date: LocalDate) {
        chosen = date
        if (layout == 2) monthDrill = date.monthValue
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(if (t.paused) "Paused · history preserved" else "Your daily pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = DayStyle.subdued)
            }
            TextButton(onClick = onTogglePause) { Text(if (t.paused) "Resume" else "Pause") }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Weeks", "Block", "Calendar").forEachIndexed { index, label ->
                FilterChip(
                    selected = layout == index,
                    onClick = { layout = index; monthDrill = 0 },
                    label = { Text(label) }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onShareImage(year, layout, monthDrill, numbers) },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Share image")
            }
            OutlinedButton(onClick = { onShareCsv(year) },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Share $year CSV")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { year--; monthDrill = 0 },
                modifier = Modifier.semantics { contentDescription = "Previous year" }) { Text("‹") }
            Text("$year", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { year++; monthDrill = 0 },
                modifier = Modifier.semantics { contentDescription = "Next year" }) { Text("›") }
            Spacer(Modifier.weight(1f))
            Text("Dates", style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
            IconToggleButton(
                checked = numbers,
                onCheckedChange = { numbers = it },
                modifier = Modifier.size(44.dp).semantics {
                    contentDescription = if (numbers) "Hide date numbers" else "Show date numbers"
                }
            ) {
                Box(
                    Modifier.size(29.dp).background(
                        if (numbers) DayStyle.paleTeal else DayStyle.canvas,
                        RoundedCornerShape(8.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("12", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (numbers) DayStyle.teal else DayStyle.subdued)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (layout == 2 && monthDrill != 0) {
                item {
                    val month = YearMonth.of(year, monthDrill)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { monthDrill = 0 }) { Text("‹ Full year") }
                        Spacer(Modifier.weight(1f))
                        Text(month.month.getDisplayName(TextStyle.FULL, Locale.UK),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    MonthCalendar(month, t, data, chosen, ::openDay)
                }
            } else {
                item {
                    if (layout == 2) {
                        Text("TAP A MONTH OR DAY TO EXPLORE", color = DayStyle.subdued,
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))
                        CalendarYear(year, t, data, numbers,
                            onTap = ::openDay,
                            onMonth = { month ->
                                monthDrill = month
                                chosen = YearMonth.of(year, month).atDay(1)
                            })
                    } else if (layout == 0) {
                        WeekGrid(year, t, data, ::openDay)
                    } else {
                        BlockGrid(year, t, data, ::openDay)
                    }
                }
            }
            item {
                DayPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val dateEditable = !t.paused && dateEntryAllowed(chosen, LocalDate.now(), t.allowPastEntries, t.allowFutureEntries)
                        Text(chosen.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(data[chosen]?.let { "Recorded: ${t.labels[it - 1]}" } ?: "No score recorded",
                            color = DayStyle.subdued)
                        if (!dateEditable) {
                            Text("This date is read-only. Change past/future entry settings in Edit to allow changes.",
                                color = DayStyle.subdued, style = MaterialTheme.typography.bodySmall)
                        } else {
                        Text("SELECT A SCORE", style = MaterialTheme.typography.labelSmall,
                            color = DayStyle.subdued)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(t.labels.size) { index ->
                                val value = index + 1
                                val chosenValue = data[chosen] == value
                                Surface(
                                    modifier = Modifier.widthIn(min = 58.dp).heightIn(min = 64.dp)
                                        .clickable { onScore(t.id, chosen, value) }
                                        .semantics { contentDescription = "Record ${t.labels[index]} for $chosen" },
                                    shape = RoundedCornerShape(13.dp),
                                    color = if (chosenValue) DayStyle.paleTeal else DayStyle.surface,
                                    border = BorderStroke(if (chosenValue) 2.dp else 1.dp,
                                        if (chosenValue) DayStyle.teal else DayStyle.outline)
                                ) {
                                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Box(Modifier.width(38.dp).height(14.dp).background(
                                            Color(Palettes.color(t, value)), RoundedCornerShape(5.dp)))
                                        Text(t.labels[index], style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        if (data.containsKey(chosen)) {
                            TextButton(onClick = { onScore(t.id, chosen, null) }) { Text("Clear this day") }
                        }
                        }
                    }
                }
            }
        item {
                Text("${data.size} recorded days  ·  ${data.keys.count { it.year == year }} in $year",
                    style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete tracker", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${t.name}?") },
                text = { Text("All entries for this tracker will be permanently deleted.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            )
        }
    }
}

/** Shared colour semantics for the three yearly grids and editable month. */
internal fun readableDateText(t: Tracker, date: LocalDate, score: Int?): String =
    "${date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))}: " +
        (score?.let { t.labels.getOrNull(it - 1) } ?: "No entry")

@Composable
fun DayCell(
    date: LocalDate?, t: Tracker, data: Map<LocalDate, Int>, numbers: Boolean,
    onTap: (LocalDate) -> Unit
) {
    val score = date?.let { data[it] }
    val bg = if (date == null) Color.Transparent else Color(Palettes.color(t, score ?: 0))
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(1.dp)
            .background(bg, RoundedCornerShape(3.dp))
            .then(if (date != null) Modifier.clickable { onTap(date) }
                .semantics { contentDescription = readableDateText(t, date, score) }
                else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (date != null && numbers) {
            Text("${date.dayOfMonth}", fontSize = 8.sp,
                color = if (bg.luminance() < 0.40f) Color.White else DayStyle.ink,
                lineHeight = 9.sp)
        }
    }
}

/** A wall calendar; weekdays are Monday–Sunday, month titles open accessible detail. */
@Composable
fun CalendarYear(
    year: Int, t: Tracker, data: Map<LocalDate, Int>, numbers: Boolean,
    onTap: (LocalDate) -> Unit, onMonth: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        (0..3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { col ->
                    val monthNumber = row * 3 + col + 1
                    val ym = YearMonth.of(year, monthNumber)
                    Column(Modifier.weight(1f)) {
                        Text(
                            ym.month.getDisplayName(TextStyle.SHORT, Locale.UK),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .clickable { onMonth(monthNumber) }
                                .semantics { contentDescription = "Open ${ym.month.getDisplayName(TextStyle.FULL, Locale.UK)} $year" }
                                .padding(vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { weekday ->
                                Text(weekday, Modifier.weight(1f), fontSize = 8.sp,
                                    textAlign = TextAlign.Center, color = DayStyle.subdued)
                            }
                        }
                        (0 until calendarWeekCount(ym)).forEach { week ->
                            Row {
                                (0..6).forEach { dow ->
                                    Box(Modifier.weight(1f)) {
                                        DayCell(calendarDateAt(ym, week * 7 + dow),
                                            t, data, numbers, onTap)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Large colour-filled month view, matching the wall-calendar's weekday positions. */
@Composable
fun MonthCalendar(
    month: YearMonth, t: Tracker, data: Map<LocalDate, Int>, chosen: LocalDate,
    onTap: (LocalDate) -> Unit
) {
    DayPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = DayStyle.subdued, style = MaterialTheme.typography.labelSmall)
                }
            }
            (0 until calendarWeekCount(month)).forEach { week ->
                Row {
                    (0..6).forEach { dow ->
                        val date = calendarDateAt(month, week * 7 + dow)
                        Box(Modifier.weight(1f).padding(2.dp).height(48.dp)) {
                            if (date != null) {
                                val day = date.dayOfMonth
                                val score = data[date]
                                val bg = Color(Palettes.color(t, score ?: 0))
                                val foreground = if (bg.luminance() < 0.40f) Color.White else DayStyle.ink
                                Surface(
                                    modifier = Modifier.fillMaxSize()
                                        .clickable { onTap(date) }
                                        .semantics { contentDescription = readableDateText(t, date, score) },
                                    shape = RoundedCornerShape(11.dp),
                                    color = bg,
                                    border = if (chosen == date) BorderStroke(3.dp, DayStyle.teal)
                                        else if (date == LocalDate.now()) BorderStroke(1.dp, DayStyle.teal)
                                        else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("$day", color = foreground,
                                            fontWeight = if (chosen == date) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Coloured days have recorded scores. Grey means no entry.",
                style = MaterialTheme.typography.bodySmall, color = DayStyle.subdued)
        }
    }
}

@Composable
fun WeekGrid(year: Int, t: Tracker, data: Map<LocalDate, Int>, onTap: (LocalDate) -> Unit) {
    val first = LocalDate.of(year, 1, 1)
    val offset = first.dayOfWeek.value - 1
    Column {
        (0..6).forEach { dow ->
            Row {
                (0..52).forEach { week ->
                    val index = week * 7 + dow - offset
                    val date = if (index in 0 until first.lengthOfYear()) first.plusDays(index.toLong()) else null
                    Box(Modifier.weight(1f)) { DayCell(date, t, data, false, onTap) }
                }
            }
        }
    }
}

@Composable
fun BlockGrid(year: Int, t: Tracker, data: Map<LocalDate, Int>, onTap: (LocalDate) -> Unit) {
    val first = LocalDate.of(year, 1, 1)
    Column {
        (0 until ((first.lengthOfYear() + 25) / 26)).forEach { row ->
            Row {
                (0..25).forEach { col ->
                    val index = row * 26 + col
                    val date = if (index < first.lengthOfYear()) first.plusDays(index.toLong()) else null
                    Box(Modifier.weight(1f)) { DayCell(date, t, data, false, onTap) }
                }
            }
        }
    }
}

private fun writeUri(activity:MainActivity,uri:Uri,contents:String){
    val stream=activity.contentResolver.openOutputStream(uri,"wt") ?: error("Document provider refused output stream")
    stream.bufferedWriter(Charsets.UTF_8).use {it.write(contents);it.flush()}
}
private fun readUri(activity:MainActivity,uri:Uri):String {
    val stream=activity.contentResolver.openInputStream(uri) ?: error("Document provider refused input stream")
    return stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val out=StringBuilder();val buffer=CharArray(8192)
        while(true){val n=reader.read(buffer);if(n<0)break;require(out.length+n<=20_000_000){"File exceeds 20 MB"};out.append(buffer,0,n)}
        out.toString()
    }
}
