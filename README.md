# EveryDayTa

**Track anything. See the patterns.**

EveryDayTa is a local-first Android app for recording a daily score or check-in for the things that matter to you: symptoms, wellbeing, habits, medication routines, activities, or anything else you choose. It is designed for quick entry and for seeing patterns across a month or an entire year.

> **Version:** 1.0.0 · **Platform:** Android (minimum API 26 / Android 8.0) · **App ID:** `com.everydayta.app`

## What you can do

- **Create unlimited trackers** with a name, colour palette and your choice of scoring scale:
  - **Done / Not done** — two explicit responses; a missing observation is different from “Not done”.
  - **Five-point Likert** — Very bad, Bad, Neutral, Good, Very good.
  - **0–10** — eleven correctly labelled choices, from 0 to 10.
  - **Custom** — 2–11 configurable levels and labels.
- **Check in quickly** from the compact Today screen, or edit permitted dates in a tracker calendar. Delete an accidental score without deleting the tracker.
- **Visualise each tracker** as a week-aligned year heatmap, continuous year block, or twelve-month wall calendar. Choose whether date numbers are visible.
- **Use Android home-screen widgets** for individual trackers and for groups of trackers. Individual widgets can move between years; a group widget stacks tracker rows for a month and can move between months. Widget period selections are independent.
- **Organise trackers into groups.** One group can have one reminder or multiple named reminder slots (for example, AM and PM), each covering a chosen subset of members.
- **Choose when entries are due:** every day, selected weekdays, every N days, selected dates each month, an ordinal weekday (for example, last Friday), or selected weekdays every N weeks. These schedules are independent of reminder times.
- **Get only relevant reminders.** Completed or non-due entries do not prompt again. Group membership owns reminders for its trackers—even if a group is paused—so pausing a group does not trigger a burst of individual reminders.
- **Tap a reminder for focused entry.** A temporary one-question-at-a-time check-in saves each answer and advances to the next item, closing itself when complete. Notifications do not require typing a score into a reply field.
- **Pause or resume** individual trackers and groups without deleting their settings, associations or history.
- **Manage trackers** with direct editing from the Trackers screen, duplicate-name protection and bulk deletion with confirmation.
- **Adjust accessibility settings**, including text size and a colour-vision-friendly palette option. Score labels remain available alongside colours.
- **Share and retain your data:** share a tracker’s visualisation as a PNG, export observations as CSV, import CSV, or create and restore a complete JSON backup.

## Quick start

1. Open EveryDayTa and choose **+ New** to create a tracker.
2. Select **Done / Not done**, **Likert**, **0–10** or **Custom**, then a palette.
3. Optionally choose a recording schedule, reminder time, and whether past or future entries can be edited.
4. Record today's value on **Today** or open the tracker’s year view.
5. Optionally create a group from **Trackers**, assign its members and configure a reminder or several named reminder slots.
6. Add widgets from the Android launcher’s widget picker and choose the tracker/group and display layout.

**Settings → Reminders & notifications** includes test notifications, including a three-step dummy group check-in (binary → Likert → 0–10). These demo check-ins do not write actual tracker observations.

## Backups, exports and privacy

EveryDayTa stores tracker records locally on your Android device. Normal usage does not require an account, an internet connection or a server. Android's automatic backup is disabled in the app configuration; **you must export your own backups** if you want to keep your data when changing phones or clearing app storage.

| Feature | Contains | Important behaviour |
| --- | --- | --- |
| **JSON backup** | Trackers, levels, colours, entries, group membership and tracker/group reminder settings | Full restore **replaces** the existing database after validation and confirmation. Save a separate backup first. |
| **CSV export** | Per-observation rows with tracker UUID, date, displayed score, level index, label and scale type | For analysis and portable records; **not a complete backup** of configuration or empty trackers/groups. |
| **CSV import** | Observations, with preview and validation | May add or update observations; inspect the preview for conflicts before confirming. |
| **Share image** | PNG of the selected tracker and visualisation period | Contains the visual information you choose to share; review before sending it. |

App-wide accessibility preferences are stored locally and are not guaranteed to be included in the JSON backup.

The current backup format is `everydayta-v8`; import compatibility is maintained for earlier supported EveryDayTa and DayGrid backups. Internal score positions are 1-based, but a 0–10 scale displays and exports **0 through 10**, and the CSV includes the internal `level_index` for an unambiguous round trip.

**Important:** The app is a personal record-keeping tool, not a medication administration verifier or a medical device. A logged medication response records what the user selected; it does not independently establish that medication was taken. Android may delay scheduled reminders because of battery and notification restrictions.

## Build from source

### Requirements

- Android Studio with an Android SDK supporting **compile SDK 35**.
- Java 17 and compatible Android Gradle Plugin / Kotlin Compose tooling.
- The repository root should contain the Gradle wrapper (`gradlew`, `gradle/wrapper/`) and a Gradle settings file that includes `:app`.

The `app/` module uses Kotlin, Jetpack Compose, local SQLite, Android `AlarmManager`/notifications and Android widgets. The module’s package and application ID are **`com.everydayta.app`**. When upgrading an existing installation, **keep that ID unchanged** to retain its on-device data.

From the Android Studio project root:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

If you have a connected emulator or device, also run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. A **publicly distributed APK should be a properly signed release build**, not this debug APK.

### GitHub release

See [v1.0.0 release notes](RELEASE_NOTES_v1.0.0.md) and [the release guide](RELEASE_GUIDE_v1.0.0.md). A convenience script, `release_v1.0.0.sh`, builds and checks the repository before tagging and publishing a **source-code release**. It does not publish a debug APK.

## Limitations and testing status

This source has undergone iterative review and standalone logic checks, but its exact v1.0.0 configuration **has not been built or device-tested in this delivery environment**. Build it with Android Studio before tagging. Test notification delivery (including AM/PM group subsets), widget resizing/arrows, backup/restore and CSV import on a real device before distributing it broadly.

## Project layout

```text
app/src/main/java/com/everydayta/app/  Kotlin app, persistence, reminders and widgets
app/src/main/res/                      Manifest resources, icons, widget layouts
app/src/test/                          JVM unit tests
app/src/androidTest/                   Android instrumented tests
```

## Licence

No software licence is specified in this release bundle. Add an explicit `LICENSE` file if you want to allow reuse or redistribution by others.
