# EveryDayTa v1.0.0 — Initial release

**Track anything. See the patterns.**

EveryDayTa 1.0.0 is the first tagged release of a local-first Android tracker designed around rapid daily check-ins, flexible reminder schedules and colourful month/year visualisations.

## Highlights

- **Flexible trackers:** Done / Not done, five-point Likert, 0–10 and custom scales with per-tracker colours and labels.
- **Fast daily recording:** compact Today overview; per-date editing; independently configurable past/future entry permissions; pause/resume without losing history.
- **Three year visualisations:** week-aligned heatmap, continuous block and twelve-month wall calendar, plus a stacked month-by-month group widget.
- **Home-screen widgets:** independent period navigation (‹ / ›); group widgets show one tracker per row.
- **Groups and multiple reminders:** combine trackers into routines and assign different members to named reminder slots—for example, AM and PM medication check-ins.
- **No duplicate reminder explosion:** a grouped tracker remains group-owned while that group is paused or its reminder disabled.
- **Focused notification check-in:** tap a reminder, answer each outstanding item in sequence, and return to the previous Android screen when complete. All scales use the same one-question-at-a-time workflow.
- **Flexible recording schedules:** daily, chosen weekdays, every N days, month dates, ordinal weekdays, and every N weeks.
- **Accessible controls:** adjustable in-app text size and a colour-vision-friendly palette option.
- **Data portability:** full JSON backups/restore, CSV observation export/import, and PNG/CSV sharing from visualisations.
- **Tracker management:** direct editing, duplicate-name protection, bulk deletion and optional custom reminder messages.

## Upgrade and data safety

- Android application ID remains **`com.everydayta.app`**; the v1.0.0 version metadata uses `versionCode 26`.
- v1.0.0 changes version metadata and release documentation relative to v0.10.6; no new data migration is introduced for this tag.
- Keep the existing app installed and install the update over it. **Do not uninstall or clear app storage** if you want to retain existing records.
- Export **Settings → Save complete backup** before upgrading or testing a restore.
- CSV is a data-oriented export, not a full backup. A JSON restore replaces the app database after validation and confirmation.

## Build and verification

This release bundle is prepared from the v0.10.6 source. The maintainer should run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # device/emulator required
```

**Status at handoff:** source packaging and metadata validation performed; a complete Android Gradle build and device test suite have **not** been executed in the delivery environment. Do not treat this as a Play Store-certified or fully device-verified build.

Release artifacts: GitHub automatically provides source archives. An installable APK should only be attached if it is built and **properly signed** with a release key. Do not upload the debug APK as the public production artifact.
