package com.everydayta.app

import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** No synthetic tracker is ever inserted into SQLite; even a successful demo
 * only updates a short-lived local test session in SharedPreferences. */
object DemoCheckIn {
    const val NUMERIC_ID = -92_001L
    const val BINARY_ID = -92_002L
    const val LIKERT_ID = -92_003L
    const val EXTRA_GROUP_DEMO = "quick_demo_group"
    const val EXTRA_GROUP_SESSION = "quick_demo_session"
    const val EXTRA_GROUP_STEP = "quick_demo_step"

    private const val PREFS = "quick_checkin_demo_session"
    private const val STEP = "step"
    private const val SESSION = "session"
    private const val DAY = "day"

    val numericTracker: Tracker = Tracker(
        id = NUMERIC_ID, name = "Test score (0–10)", labels = ScaleSemantics.zeroToTenLabels,
        palette = 0, hour = 12, minute = 0, reminder = false,
        scaleType = ScaleSemantics.ZERO_TO_TEN
    )
    val binaryTracker: Tracker = Tracker(
        id = BINARY_ID, name = "Test medication (Done / Not done)", labels = ScaleSemantics.doneNotDoneLabels,
        palette = 0, hour = 12, minute = 0, reminder = false,
        scaleType = ScaleSemantics.DONE_NOT_DONE
    )
    val likertTracker: Tracker = Tracker(
        id = LIKERT_ID, name = "Test mood (Likert)", labels = ScaleSemantics.likertLabels,
        palette = 2, hour = 12, minute = 0, reminder = false,
        scaleType = ScaleSemantics.LIKERT
    )
    val demoGroup = listOf(binaryTracker, likertTracker, numericTracker)

    data class GroupStep(val session: Int, val step: Int, val tracker: Tracker)

    fun trackerFor(id: Long): Tracker? = when (id) {
        NUMERIC_ID -> numericTracker
        BINARY_ID -> binaryTracker
        LIKERT_ID -> likertTracker
        else -> null
    }

    fun isGroupDemo(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_GROUP_DEMO, false)

    fun isValidRequest(intent: Intent): Boolean =
        !isGroupDemo(intent) && intent.getBooleanExtra(QuickCheckIn.EXTRA_DEMO, false) &&
            intent.getLongExtra(QuickCheckIn.EXTRA_GROUP, -1L) == -1L &&
            trackerFor(intent.getLongExtra(QuickCheckIn.EXTRA_TRACKER, -1L)) != null &&
            intent.getStringExtra(QuickCheckIn.EXTRA_DAY) == LocalDate.now().toString()

    @Synchronized
    fun startGroup(context: Context): GroupStep {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val session = if (p.getInt(SESSION, 0) == Int.MAX_VALUE) 1 else p.getInt(SESSION, 0) + 1
        p.edit().putInt(SESSION, session).putInt(STEP, 0)
            .putString(DAY, LocalDate.now().toString()).commit()
        return GroupStep(session, 0, demoGroup[0])
    }

    /** Activity may continue after each answer without replacing its original intent.
     * The session ID and date must still match the active demo. */
    @Synchronized
    fun groupState(context: Context, intent: Intent): GroupStep? {
        if (!isGroupDemo(intent) ||
            !intent.getBooleanExtra(QuickCheckIn.EXTRA_DEMO, false) ||
            intent.getLongExtra(QuickCheckIn.EXTRA_GROUP, -1L) != -1L ||
            intent.getStringExtra(QuickCheckIn.EXTRA_DAY) != LocalDate.now().toString()) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getInt(SESSION, 0) == 0 ||
            p.getInt(SESSION, 0) != intent.getIntExtra(EXTRA_GROUP_SESSION, -1) ||
            p.getString(DAY, null) != LocalDate.now().toString()) return null
        val step = p.getInt(STEP, -1)
        return if (step in demoGroup.indices)
            GroupStep(p.getInt(SESSION, 0), step, demoGroup[step]) else null
    }

    @Synchronized
    fun activeGroupStep(context: Context): GroupStep? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString(DAY, null) != LocalDate.now().toString()) return null
        val step = p.getInt(STEP, -1)
        return if (p.getInt(SESSION, 0) > 0 && step in demoGroup.indices)
            GroupStep(p.getInt(SESSION, 0), step, demoGroup[step]) else null
    }

    /** Only the focused check-in screen can advance a demo session. */
    @Synchronized
    fun advanceGroup(context: Context, intent: Intent, trackerId: Long,
                     level: Int, fromNotification: Boolean): Boolean {
        val state = groupState(context, intent) ?: return false
        // An obsolete notification-side action is never allowed to progress a demo.
        if (fromNotification || state.tracker.id != trackerId ||
            level !in 1..state.tracker.labels.size) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(STEP, state.step + 1).commit()
    }

}
