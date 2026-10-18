#!/usr/bin/env bash
# Publish a v1.0.0 GitHub source release from the local Android Studio repository.
# Run only after copying the v1.0.0 app/ and documentation into your repository.
set -euo pipefail

TAG="v1.0.0"
REPO="chrissyhroberts/EveryDayTa"

[[ -f settings.gradle.kts && -f app/build.gradle.kts && -x ./gradlew ]] || {
  echo "Run this from the Android Studio repository root (with executable gradlew)." >&2; exit 1;
}
[[ "$(git branch --show-current)" == "main" ]] || {
  echo "Switch to your main branch before releasing." >&2; exit 1;
}
[[ "$(git remote get-url origin)" == *"chrissyhroberts/EveryDayTa"* ]] || {
  echo "Unexpected GitHub remote. Inspect 'git remote -v' before releasing." >&2; exit 1;
}
command -v gh >/dev/null || { echo "Install GitHub CLI: brew install gh" >&2; exit 1; }
gh auth status >/dev/null

for f in README.md RELEASE_NOTES_v1.0.0.md .gitignore; do
  [[ -f "$f" ]] || { echo "Missing $f" >&2; exit 1; }
done
if ! grep -q 'versionCode = 26' app/build.gradle.kts ||    ! grep -q 'versionName = "1.0.0"' app/build.gradle.kts ||    ! grep -q 'applicationId = "com.everydayta.app"' app/build.gradle.kts; then
  echo "Version or applicationId mismatch in app/build.gradle.kts." >&2; exit 1
fi

git fetch origin --tags
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "Tag $TAG already exists; stopping to avoid overwriting history." >&2; exit 1
fi

# Verify that current HEAD is based on the current upstream main.
git merge-base --is-ancestor origin/main HEAD || {
  echo "Local main is behind/diverged from origin/main. Reconcile before release." >&2; exit 1;
}

echo "Building and testing the release source..."
./gradlew :app:testDebugUnitTest :app:assembleDebug

echo "Review changes staged for release:"
git add app README.md RELEASE_NOTES_v1.0.0.md .gitignore RELEASE_GUIDE_v1.0.0.md release_v1.0.0.sh
git diff --cached --check
git diff --cached --stat
read -r -p "Commit, tag and publish source release $TAG? Type RELEASE to proceed: " confirm
[[ "$confirm" == "RELEASE" ]] || { echo "Cancelled; staged changes remain."; exit 0; }

git diff --cached --quiet && { echo "No staged changes to commit." >&2; exit 1; }
git commit -m "Release EveryDayTa v1.0.0"
git tag -a "$TAG" -m "EveryDayTa v1.0.0"
git push origin main
git push origin "$TAG"
gh release create "$TAG" --repo "$REPO" --verify-tag   --title "EveryDayTa v1.0.0" --notes-file RELEASE_NOTES_v1.0.0.md

echo "Published: https://github.com/$REPO/releases/tag/$TAG"
echo "Source-only release: no unsigned/debug APK was uploaded."
