# Instant Month Widget Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Month and Multi widget navigation display cached target-month content immediately and never publish a loading skeleton during normal navigation.

**Architecture:** Extend the existing bounded in-memory month-page cache with exact-generation and latest-known lookup tiers under the existing model namespace. Navigation publishes an exact or latest-known complete page immediately; on a total miss it leaves the currently rendered month untouched until the complete target page is loaded atomically.

**Tech Stack:** Kotlin, Android `RemoteViews`, coroutines, JUnit 4, kotlinx-coroutines-test, Gradle/JDK 17.

## Global Constraints

- Preserve Month and Multi widget header/content consistency.
- Do not persist page snapshots across process death.
- Do not reuse pages across locale, first-day-of-week, task-color, completed-task visibility, hidden-collection, render-version, or time-zone namespaces.
- Keep the page-model cache bounded to 64 total entries.
- Preserve revision-based latest-navigation-wins behavior and task priority animations.

---

### Task 1: Two-Tier Month Page Cache

**Files:**
- Modify: `app/src/main/java/com/kgs/calendar/widget/WidgetState.kt`
- Test: `app/src/test/java/com/kgs/calendar/widget/WidgetMonthCacheTest.kt`

**Interfaces:**
- Produces: `WidgetMonthPageFreshness`, `WidgetMonthPageLookup`, and `KgsWidgetMonthPageCache.getForNavigation(month, settings, zoneId, generation)`.
- Preserves: `KgsWidgetMonthPageCache.get(...)` as the exact-generation API used by cache warming and pre-render update checks.

- [ ] **Step 1: Write failing exact/latest and namespace-isolation tests**

```kotlin
@Test
fun navigationLookupPrefersCurrentGenerationThenFallsBackToLatestKnown() {
    val month = YearMonth.of(2027, 2)
    val settings = WidgetRenderSettings(firstDayOfWeek = DayOfWeek.MONDAY)
    val page = WidgetMonthPage(month, 5, emptyList())
    KgsWidgetMonthPageCache.put(month, settings, "cache-test-a", page, generation = 7)

    assertEquals(
        WidgetMonthPageFreshness.CurrentGeneration,
        KgsWidgetMonthPageCache.getForNavigation(month, settings, "cache-test-a", generation = 7)?.freshness,
    )
    assertEquals(
        WidgetMonthPageFreshness.LatestKnown,
        KgsWidgetMonthPageCache.getForNavigation(month, settings, "cache-test-a", generation = 8)?.freshness,
    )
}

@Test
fun latestKnownNavigationPageDoesNotCrossModelNamespace() {
    val month = YearMonth.of(2027, 3)
    val monday = WidgetRenderSettings(firstDayOfWeek = DayOfWeek.MONDAY)
    val sunday = monday.copy(firstDayOfWeek = DayOfWeek.SUNDAY)
    KgsWidgetMonthPageCache.put(month, monday, "cache-test-b", WidgetMonthPage(month, 5, emptyList()), 4)

    assertEquals(null, KgsWidgetMonthPageCache.getForNavigation(month, sunday, "cache-test-b", 5))
    assertEquals(null, KgsWidgetMonthPageCache.getForNavigation(month, monday, "cache-test-c", 5))
}
```

- [ ] **Step 2: Run the cache tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.widget.WidgetMonthCacheTest
```

Expected: compilation fails because `WidgetMonthPageFreshness` and `getForNavigation` do not exist.

- [ ] **Step 3: Implement exact/latest keys in the same bounded store**

```kotlin
internal enum class WidgetMonthPageFreshness { CurrentGeneration, LatestKnown }

internal data class WidgetMonthPageLookup(
    val page: WidgetMonthPage,
    val freshness: WidgetMonthPageFreshness,
)

fun getForNavigation(
    month: YearMonth,
    settings: WidgetRenderSettings,
    zoneId: String,
    generation: Long = WidgetDataGeneration.current(),
): WidgetMonthPageLookup? = synchronized(pages) {
    pages[generationKey(month, settings, zoneId, generation)]?.let { page ->
        return@synchronized WidgetMonthPageLookup(page, WidgetMonthPageFreshness.CurrentGeneration)
    }
    pages[latestKey(month, settings, zoneId)]?.let { page ->
        WidgetMonthPageLookup(page, WidgetMonthPageFreshness.LatestKnown)
    }
}
```

Keep `get(...)` generation-specific. Update `put(...)` to write both `generationKey(...)` and `latestKey(...)`, then run the existing least-recently-used pruning loop once so the combined store remains at 64 entries.

- [ ] **Step 4: Run the cache tests and verify GREEN**

Run the command from Step 2.

Expected: `WidgetMonthCacheTest` passes.

- [ ] **Step 5: Commit the cache task**

```powershell
git add app/src/main/java/com/kgs/calendar/widget/WidgetState.kt app/src/test/java/com/kgs/calendar/widget/WidgetMonthCacheTest.kt
git commit -m "perf: retain latest month widget pages"
```

---

### Task 2: Complete-Page-Only Month Navigation

**Files:**
- Modify: `app/src/main/java/com/kgs/calendar/widget/WidgetMonthNavigation.kt`
- Modify: `app/src/main/java/com/kgs/calendar/widget/KgsWidgetProvider.kt`
- Test: `app/src/test/java/com/kgs/calendar/widget/WidgetMonthNavigationTest.kt`

**Interfaces:**
- Consumes: `KgsWidgetMonthPageCache.getForNavigation(...)` and `WidgetMonthPageFreshness` from Task 1.
- Produces: `selectMonthNavigationInitialPage(cached: WidgetMonthPage?): WidgetMonthPage?` where `null` means retain the currently displayed widget until authoritative loading finishes.

- [ ] **Step 1: Replace skeleton expectations with complete-page-only tests**

```kotlin
@Test
fun cachedTargetUsesCompletePageImmediately() {
    val cached = page(YearMonth.of(2026, 2))
    val initial = selectMonthNavigationInitialPage(cached)

    assertTrue(initial === cached)
}

@Test
fun cacheMissKeepsCurrentWidgetUntilCompleteTargetIsLoaded() {
    assertEquals(null, selectMonthNavigationInitialPage(null))
}
```

- [ ] **Step 2: Run the navigation tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.widget.WidgetMonthNavigationTest
```

Expected: compilation fails because the existing selector requires a skeleton argument and returns a non-null staged result.

- [ ] **Step 3: Implement nullable initial selection and navigation lookup**

```kotlin
internal fun selectMonthNavigationInitialPage(
    cached: WidgetMonthPage?,
): WidgetMonthPage? = cached
```

Delete `MonthNavigationPageStage` and `MonthNavigationInitialPage`; they have no remaining role after skeleton publication is removed.

In `navigateMonth`:

```kotlin
val cachedLookup = KgsWidgetMonthPageCache.getForNavigation(
    snapshot.month,
    settings,
    zoneId.id,
    generation,
)
val initialPage = selectMonthNavigationInitialPage(cachedLookup?.page)
if (
    initialPage != null &&
    !applyIfCurrent(
        renderer.render(
            kind = kind,
            snapshot = snapshot,
            options = options,
            settings = settings,
            page = initialPage,
            hasCompleteData = true,
        ),
    )
) {
    return
}
if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return

if (cachedLookup?.freshness == WidgetMonthPageFreshness.CurrentGeneration) {
    warmWidgetMonthPageCache(context, zoneId, snapshot.month, settings)
    return
}
```

Remove creation/publication of `pageSource.empty(...)`. Keep authoritative loading for latest-known and total-miss paths. Compare `cachedLookup?.page` with the loaded authoritative page before publishing the second update. Preserve the existing generation-stability cache rule and revision checks.

- [ ] **Step 4: Run focused cache and navigation tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.kgs.calendar.widget.WidgetMonthCacheTest --tests com.kgs.calendar.widget.WidgetMonthNavigationTest
```

Expected: both test classes pass.

- [ ] **Step 5: Run full verification and install**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\ADB\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: all tests pass, debug APK builds, and installation succeeds.

- [ ] **Step 6: Verify on-device navigation**

Clear `KgsWidget` logs, navigate previous/next repeatedly, and verify:

- Exact/latest cache paths show complete target content without a skeleton.
- A total miss retains the old complete month until one atomic target update.
- Header and grid always refer to the same month.
- Every same-widget trace reports `concurrent=1`.
- Return widget 98 to the current month after testing.

- [ ] **Step 7: Commit the navigation task**

```powershell
git add app/src/main/java/com/kgs/calendar/widget/WidgetMonthNavigation.kt app/src/main/java/com/kgs/calendar/widget/KgsWidgetProvider.kt app/src/test/java/com/kgs/calendar/widget/WidgetMonthNavigationTest.kt
git commit -m "perf: navigate month widget without skeletons"
```
