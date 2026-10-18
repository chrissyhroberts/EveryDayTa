# EveryDayTa v1.0.0 — Release guide

The GitHub repository is `chrissyhroberts/EveryDayTa` (default branch: `main`). The upstream README was only `a` when this bundle was prepared, and upstream `app/build.gradle.kts` still contained the initial Android Studio skeleton; **the working local Android Studio project must receive the updated `app/` module and release files before publishing**. Do not overwrite your working root Gradle files with the older remote skeleton.

## Preflight

1. Make a JSON backup on your phone. Keep your installed application ID `com.everydayta.app` unchanged.
2. Copy this bundle's `app/` into the **root of your local EveryDayTa Android Studio repository**, replacing the existing `app/`. Keep its existing Gradle wrapper and root settings. Copy `README.md`, `RELEASE_NOTES_v1.0.0.md`, and `.gitignore` to the root. Review changes first, especially if you already have a customised `.gitignore`.
3. Confirm that `app/build.gradle.kts` says `versionCode = 26` and `versionName = "1.0.0"` and still says `applicationId = "com.everydayta.app"`.
4. Test in Android Studio or in terminal:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # if connected device/emulator available
```

5. On device, verify sequential three-step notification test, AM/PM reminders, widget period arrows, pause rules and a JSON backup/restore on a spare test profile or emulator. A restore overwrites current data; do not test it against irreplaceable records.

## Publish source release with GitHub CLI

After reviewing the local Git diff, use the provided `release_v1.0.0.sh` script or run the commands below **from the repository root**:

```bash
# Install gh if needed: brew install gh
# Authenticate if needed: gh auth login

git status --short
git remote -v
./gradlew :app:testDebugUnitTest :app:assembleDebug

git add app README.md RELEASE_NOTES_v1.0.0.md .gitignore
git diff --cached --stat
git diff --cached --check
git commit -m "Release EveryDayTa v1.0.0"

git tag -a v1.0.0 -m "EveryDayTa v1.0.0"
git push origin main
git push origin v1.0.0

gh release create v1.0.0 --repo chrissyhroberts/EveryDayTa \
  --verify-tag --title "EveryDayTa v1.0.0" \
  --notes-file RELEASE_NOTES_v1.0.0.md
```

**Do not push** if `git remote -v` is not your intended repository, the build fails, or `git diff --cached` includes credentials or unexpected files. If `v1.0.0` already exists, investigate rather than deleting/replacing the tag.

## Optional signed release APK

`assembleDebug` is for testing only. A distribution-ready APK must be built with release signing configured **outside version control**. Once you have generated and verified a signed APK, upload it with:

```bash
gh release upload v1.0.0 path/to/your-signed-release.apk \
  --repo chrissyhroberts/EveryDayTa
```

If release signing is not yet configured, publish a **source-only GitHub release**; GitHub provides source ZIP/tarball automatically. If distributing through Google Play later, a signed AAB and Play Store release process are separate.
