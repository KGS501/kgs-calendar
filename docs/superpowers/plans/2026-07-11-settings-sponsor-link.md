# Settings Sponsor Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a localized Settings row above `Report a bug` that opens KGS Calendar's GitHub Sponsors page.

**Architecture:** The existing main-settings `SettingsMenuRow` directly launches external URLs with Android's `ACTION_VIEW` intent. Add the sponsor target as an internal URL constant in the same UI file, add bilingual text resources, and insert a row that reuses that established launch path.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android resources, JUnit 4, Gradle.

## Global Constraints

- Work in `C:\Users\fromb\Documents\GitHub\kgs_calendar` using `./gradlew.bat` with JDK 17.
- Preserve the existing `SettingsMenuRow` external-link behavior; do not introduce persistence, permissions, network calls, or an in-app browser.
- Place the sponsor action immediately above `Report a bug` in the main Settings list.
- Use the exact URL `https://github.com/sponsors/KGS501`.
- Add English and German resources.

---

### Task 1: Define and verify the sponsor target

**Files:**
- Modify: `app/src/main/java/com/kgs/calendar/ui/KgsCalendarApp.kt:10049-10051`
- Create: `app/src/test/java/com/kgs/calendar/ui/SettingsExternalLinksTest.kt`

**Interfaces:**
- Produces: `internal const val SponsorProjectUrl: String`, available to the Compose settings screen and the package-level unit test.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/kgs/calendar/ui/SettingsExternalLinksTest.kt`:

```kotlin
package com.kgs.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsExternalLinksTest {
    @Test
    fun sponsorProjectUrlTargetsKgs501GitHubSponsorsPage() {
        assertEquals("https://github.com/sponsors/KGS501", SponsorProjectUrl)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.ui.SettingsExternalLinksTest`

Expected: FAIL because `SponsorProjectUrl` is unresolved.

- [ ] **Step 3: Add the minimal production constant**

In `app/src/main/java/com/kgs/calendar/ui/KgsCalendarApp.kt`, change the external-link constants to:

```kotlin
private const val PrivacyPolicyUrl = "https://kgs501.github.io/kgs-calendar/"
private const val BugReportIssuesUrl = "https://github.com/KGS501/kgs-calendar/issues"
internal const val SponsorProjectUrl = "https://github.com/sponsors/KGS501"
private const val GoogleCalendarSyncSelectUrl = "https://calendar.google.com/calendar/syncselect"
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.ui.SettingsExternalLinksTest`

Expected: PASS with one test and zero failures.

### Task 2: Add the localized sponsor Settings row

**Files:**
- Modify: `app/src/main/res/values/strings.xml` near `report_bug`
- Modify: `app/src/main/res/values-de/strings.xml` near `report_bug`
- Modify: `app/src/main/java/com/kgs/calendar/ui/KgsCalendarApp.kt:7995-8009`

**Interfaces:**
- Consumes: `SponsorProjectUrl: String` from Task 1.
- Produces: A main Settings `SettingsMenuRow` immediately above the existing bug-report row.

- [ ] **Step 1: Add the new bilingual resource identifiers**

Add directly before the existing `report_bug` strings in `app/src/main/res/values/strings.xml`:

```xml
<string name="sponsor_project">Sponsor this project</string>
<string name="sponsor_project_summary">Open GitHub Sponsors</string>
```

Add directly before the existing `report_bug` strings in `app/src/main/res/values-de/strings.xml`:

```xml
<string name="sponsor_project">Projekt unterst&#252;tzen</string>
<string name="sponsor_project_summary">GitHub Sponsors &#246;ffnen</string>
```

- [ ] **Step 2: Insert the sponsor row above the bug-report row**

In the `SettingsDestination.Main` column in `app/src/main/java/com/kgs/calendar/ui/KgsCalendarApp.kt`, insert this immediately before the existing `SettingsMenuRow` whose title is `R.string.report_bug`:

```kotlin
SettingsMenuRow(
    title = stringResource(R.string.sponsor_project),
    value = stringResource(R.string.sponsor_project_summary),
    leadingIcon = Icons.Default.Favorite,
) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SponsorProjectUrl)))
}
```

Add this import beside the other filled Material icon imports:

```kotlin
import androidx.compose.material.icons.filled.Favorite
```

- [ ] **Step 3: Run the focused test and full unit-test suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.ui.SettingsExternalLinksTest`

Expected: PASS with one test and zero failures.

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: PASS with zero failures.

- [ ] **Step 4: Build the debug APK and perform launcher verification**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

Install with: `./ADB/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`

Verify manually: Open Settings, confirm `Sponsor this project` sits directly above `Report a bug`, then tap it and confirm Android opens `https://github.com/sponsors/KGS501`.

- [ ] **Step 5: Commit the implementation**

```powershell
git add app/src/main/java/com/kgs/calendar/ui/KgsCalendarApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/test/java/com/kgs/calendar/ui/SettingsExternalLinksTest.kt
git commit -m "feat: add settings sponsor link"
```
