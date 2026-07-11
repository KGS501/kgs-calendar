# Widget Performance Overhaul — Analysis & Implementation Plan (rev. 2)

**Status:** Core overhaul implemented and device-verified on API 36; conditional experiments recorded below
**Scope:** All 5 home-screen widgets (Agenda, Month, Tasks, Multi, Day)
**Goal:** Keep current widget functionality **and design/animation fidelity exactly as-is**, but make interactions feel instant (<150 ms perceived), animations smooth, and rendering crisp.
**Revision note:** rev. 2 incorporates an independent review that verified findings on a real device and corrected several rev. 1 assumptions. Where rev. 1 proposed "predetermined" changes (WebP, exact-pixel scale, parallel rendering), rev. 2 reclassifies them as gated experiments. The implementation followed that measurement-first rule; results and gate decisions are in section 8.

---

## 1. Executive summary

The widget system renders nearly everything the user sees (task cards, event cards, month event chips, day-grid rows, including the *text*) in the app process into `Bitmap`s, PNG-compresses them at quality 100, and serves them to the launcher through a `ContentProvider` (`content://…widget.images` URIs) or inline. Animations exist through two mechanisms — both legitimate given RemoteViews' lack of an animation API, both currently paying far more than they need to:

1. **Flip-book priority motion:** each animated task row binds 20 (Tasks) or 30 (Agenda/Day/Multi) full-size pre-rendered bitmap frames into a `ViewFlipper`. There is **no cap on how many rows animate** in Tasks/Agenda/Multi (the 15-row limit exists only in the Day grid), so a task-heavy widget can render/encode/write *thousands* of frames per update.
2. **App-driven frame pushing:** subtask expand/collapse, sort-button morph, and all-day expansion loop in the app, re-rendering full widget content and calling `partiallyUpdateAppWidget()` every 34–42 ms.

**Measured on-device (debug build, currently bound widgets):**
- Month widget no-op refresh: **~1.62 s** rebuilding 121 items *before* the "Skipped unchanged Month widget" short-circuit fires — because the signature is only computed **after** the full RemoteViews tree and all chip bitmaps are built.
- Tasks widget no-op refresh: **~47 ms** — its signature check happens before rendering. This is the proof that signature-before-render is the pattern to generalize.
- Month widget cache directory: 187 PNGs / ~1.1 MB.

**Product constraint (binding): zero feature and design regressions.** The priority motion (glow + card lift + scale pulse + P1 shake, with card text moving as part of the card) is a hard requirement on widgets that display task cards. Because the text moves with the card, animated frames must remain per-task rendered bitmaps — that mechanism stays.

**Strategy (two-track, measurement-first):**
- **Track A — stop unnecessary work in the existing architecture (Phases 0–2).** Compute signatures before rendering, reuse loaded row snapshots, coalesce/serialize updates, skip bitmap *rendering* (not just encoding) on cache hits, fix the cache-eviction thrash. Zero visual change, low risk, and — per the Tasks-vs-Month measurement above — potentially most of the win.
- **Track B — convert what measurement says still needs it (Phases 3–5).** Month widget (which has none of the protected animations) goes native; static task/event rows go native behind prototype + screenshot gates; encode/scale/parallelism changes run as benchmarked experiments, not defaults.

---

## 2. Current architecture (as-built)

### 2.1 Components

| File | Role |
|---|---|
| `app/src/main/java/com/kgs/calendar/widget/KgsWidgetProvider.kt` (7,505 lines) | Everything: 5 provider classes, update orchestration (`KgsWidgetUpdater`), data loading (`KgsWidgetDataSource`), renderer (`KgsWidgetRenderer`), row models, all bitmap painting code, `RemoteViewsService` factories, animation drivers |
| `WidgetBitmapCache.kt` (`KgsWidgetBitmapUriStore`) | PNG-compresses bitmaps (quality 100) to `files/widget_images/day_<widgetId>/<sha256>.png`, max 320 files per widget, returns `content://` URIs |
| `KgsWidgetImageProvider.kt` | Exported `ContentProvider` the launcher uses to open those PNGs |
| `WidgetState.kt` | SharedPreferences-backed nav state, in-memory caches (month page cache, collection row cache, update signatures, interaction tokens) |
| `WidgetUpdateScheduler.kt` | Coroutine scope (`Dispatchers.IO`), debounce for resize, latest-wins jobs. **No coalescing or per-widget serialization of generic updates** — overlapping Month renders observed in device logs |
| `WidgetMonthModel.kt`, `WidgetMonthPageSource.kt`, `WidgetMonthNavigation.kt`, `WidgetMonthRenderSpec.kt` | Month page computation + revision-based nav state machine (well designed; keep) |
| `WidgetTaskCardRenderer.kt` | Canvas painter + single source of truth for task-card metrics and the priority-motion effect math |
| `res/layout/widget_task_item.xml` etc. | Row layouts: `ViewFlipper` with 20 `ImageView` children (30 for agenda-style rows), a background-art `ImageView`, and `TextView`s with `textColor="#00000000"` as invisible click targets |

### 2.2 Render pipeline for one Tasks-widget update (API 31+ path)

1. `KgsWidgetUpdater.update()` (`KgsWidgetProvider.kt:1092`) → `collectionSnapshot()` — Room snapshot queries, builds up to 150 `WidgetListRow`s, computes a signature. **If the signature matches the last applied one, the update stops here (~47 ms measured).**
2. On change: `renderer.render()` → `renderCollectionWidget()` (`:4364`) — which **loads all rows a second time** (`dataSource.listRows` at `:4407`); the snapshot from step 1 is not reused for rendering.
3. `bindDirectCollectionItems()` (`:865`) eagerly materializes every row's RemoteViews into `RemoteCollectionItems` — including all bitmaps. (Android's collection-widget docs explicitly warn this transport is unsuitable for collections containing many bitmaps; the service-backed `RemoteViewsFactory` path is the lazy alternative and already exists as the pre-S/fallback path.)
4. Per row (`WidgetListRow.toRemoteViews`, `:5883`):
   - **Non-animated row:** one `taskRowBackgroundBitmap()` — `RGB_565`, sized `artWidthDp × 56dp` at `density × 1.15` supersampling, where `artWidthDp` comes from the actual widget size via `collectionArtWidthDp()` (`:6903`; 360 dp is only the no-options fallback). Card shape, hierarchy lines, **and the title/meta text are painted into it**. Shipped inline via `setImageViewBitmap`.
   - **Animated ("priority motion") row:** `taskPriorityMotionBitmap()` runs **20–30×** — each frame `ARGB_8888` at 1.15× supersample, each PNG-encoded at q100 and written to disk via `KgsWidgetBitmapUriStore.put()`, each bound to a `ViewFlipper` child by content URI. **No row cap** in Tasks/Agenda/Multi.
5. `updateAppWidget()` ships the tree; the launcher then performs one `ContentResolver.openFileDescriptor()` IPC + PNG decode **per frame per animated row**, and the `ViewFlipper` flips at `flipInterval`.

### 2.3 The interaction pipeline (why taps take >1 s)

Example — subtasks chevron (`KgsWidgetActionReceiver`, `:530`): broadcast → app wake → **two** full snapshot loads (before + after) → `animateTasksSubtaskToggle()` (`:790`) pushes ~5 transition frames, each rebuilding the **entire** `RemoteCollectionItems` with all row bitmaps re-rendered at 0.78× scale (`lightweightTaskTransition` — the blur visible during transitions), `delay(34ms)` between frames → final full-quality frame. Same pattern: sort morph (`:705`), day all-day expansion (`:1378`).

### 2.4 Month widget specifics — the measured 1.62 s no-op

`renderMonthWidget` (`:3594`) → `renderMonthPageResult` (`:3609`) **builds the complete RemoteViews tree first** — several hundred nested `RemoteViews.addView()` calls (header + up to 6 week rows × [7 cell shells + chip-lane rows + bottom-fade rows + today-border rows]) and renders every span-chip bitmap (`monthSpanChipBitmap`, 3× density, PNG'd to disk) — and only **then** computes the signature that `KgsWidgetUpdater.update()` (`:1153`) uses to skip unchanged widgets. The skip saves the Binder apply, not the 1.6 s build. Month navigation (`navigateMonth`, `:1229`) is better designed (revision state machine, page cache, skeleton-then-authoritative) but pays the same per-render build cost.

### 2.5 Scheduling

`KgsWidgetUpdateScheduler` debounces only resize events. Generic updates (`updateAll` from `CalendarViewModel.kt:532-616`, `AppGraph.kt`, sync completion, `USER_PRESENT`, `updatePeriodMillis=1800000`) are neither coalesced nor serialized per widget — concurrent jobs can render the same widget simultaneously (observed in logs), multiplying all costs above.

### 2.6 Cache-store issues (`WidgetBitmapCache.kt`)

- `put()` receives an **already-rendered bitmap** even when the file exists — cache hits skip the PNG encode but not the bitmap render, hashing, `lastModified` touch, or pruning.
- `prune()` runs a full `listFiles()` + sort on **every put**.
- `MAX_FILES_PER_WIDGET = 320` with no generation awareness: an update writing more than 320 files (possible since animated rows are uncapped) evicts files from the *same* update deterministically — every subsequent update misses again (cyclic thrash). The Day/Agenda worst case exceeds this easily.
- Global `@Synchronized` on `put()` serializes all widgets' writes.

### 2.7 Miscellaneous

- `ACTION_CONFIGURATION_CHANGED` manifest filters (`AndroidManifest.xml`) are dead — never delivered to manifest receivers.
- `minSdk = 26`; the direct-`RemoteCollectionItems` path is gated to API 31+; pre-S uses `KgsWidgetCollectionService` factories (which also pre-render all rows in `onDataSetChanged`, via `runBlocking`).
- Quality artifacts: `RGB_565` static card backgrounds (gradient banding), 0.78× blur during transitions, bitmap text slightly softer than native text even at 1.15× supersample.

---

## 3. Root causes ranked by impact (verified)

1. **Month: full tree + chip bitmaps built before the signature check** — measured 1.62 s for a no-op. `:3609-3653`, `:1146-1156`.
2. **Uncapped flip-book rendering with no render-skip on cache hit** — every update re-renders (and on miss re-encodes) 20–30 frames per animated row; cache-thrash above 320 files makes misses permanent. `:5975-6023`, `WidgetBitmapCache.kt`.
3. **App-driven multi-frame transition loops re-rendering all rows per frame at 0.78×** — the low-fps, blurry transitions. `:705-842`, `:1378-1405`.
4. **Double row-load per changed collection update** (signature pass + render pass). `:1113`, `:4407`.
5. **No update coalescing/serialization** — overlapping renders multiply everything. `WidgetUpdateScheduler.kt`.
6. **Eager `RemoteCollectionItems` materialization of up to 150 bitmap-bearing rows** — against platform guidance; transport choice untested vs the lazy service path. `:865-881`.
7. **Month's nested-tree size and per-chip 3× bitmaps** — heavy even when signature logic is fixed. `:3706-3902`, `:5629`.
8. Minor: per-put prune + global lock, dead manifest filters, `RGB_565` banding.

### Why the Google Calendar widget feels snappy, for contrast

Native `TextView`s and tinted drawables rendered by the launcher; a lazy collection factory; tiny view-command payloads instead of megapixels; no idle animation; press feedback from launcher-side ripples. This app's design goals (moving task cards) legitimately require more than that — but only for the rows that actually animate.

---

## 4. Target architecture

**Principle: the launcher renders everything static; the app renders only what genuinely moves or what RemoteViews cannot draw — and never renders the same frame twice.**

### 4.0 Per-widget strategy

| Widget | Priority motion today? | Strategy |
|---|---|---|
| **Month** | No | Track A fixes first (signature-before-render alone may fix it — measure). Then native chips/cells behind a prototype gate. Zero animation constraints here. |
| **Tasks** | Yes (uncapped rows × 20 frames) | Animated rows keep the full-fidelity flip-book with render-skip + thrash-proof cache. Non-animated rows go native behind the Phase-3 prototype gate. Transitions keep choreography. |
| **Agenda** | Yes (30 frames) | Same as Tasks; pure event rows go native. |
| **Multi** | Yes (agenda part) + month part | Month part follows Month strategy; agenda part follows Agenda. |
| **Day** | Yes (hour-grid rows, capped 15) | Grid/timeline art stays bitmap (minute-positioned lanes aren't expressible in RemoteViews); render-skip + exact caching. Priority motion visually unchanged. |

### 4.1 Native rendering for static content (gated by Phase-3 prototypes)

Non-animated task rows, event/agenda cards, section headers, month cells/chips become real layouts: `TextView`s with visible colors and `ellipsize=end`, rounded-rect backgrounds, tinted icons, thin `View`s for hierarchy lines, `StrikethroughSpan` for cancelled, dashed-stroke drawable for tentative. Reuse `TaskCardBaseSpec` metrics so native rows align pixel-for-pixel with bitmap frames (transitions switch between them).

**API strategy for dynamic rounded colors (minSdk 26):** `RemoteViews.setColorStateList` is API 31+. Pre-S, use an `ImageView` bearing a white rounded-rect shape drawable + `setInt(viewId, "setColorFilter", color)` (remotable since long before 26) with native text stacked above; on S+ either technique works. The Phase-3 prototype must validate both branches.

**Design parity is the acceptance bar** — overlay screenshots per row type; permitted deltas are only automatic improvements (crisper native text, no transition blur, no 565 banding).

### 4.2 Priority motion stays at full fidelity — optimize the pipeline, not the animation

Per `WidgetTaskCardRenderer.effect()`: glow breathing + ±1 dp lift + 1.8 % scale pulse + P1 micro-shake, card content moving as one unit. Frames therefore remain per-task rendered bitmaps — **frame sharing across tasks is impossible without dropping the moving text; do not attempt it.** The flip-book stays byte-for-byte identical on screen. Around it:

- **Render-skip on cache hit (certain win, Track A).** Add `getIfPresent(cacheKey): Uri?` to the store; change image-binding helpers to take `bitmapProvider: () -> Bitmap` so the bitmap is only created on a miss. Unchanged rows → zero pixel work.
- **Thrash-proof cache (certain win, Track A).** Per-update generation marking: `beginUpdate(widgetId)` / `endUpdate(widgetId)`; prune runs once per update at `endUpdate`, never evicting files touched in the current or previous generation; cap sized from observed per-widget needs with headroom rather than a fixed 320.
- **Animation-row cap for Tasks/Agenda/Multi — owner decision required.** Today unbounded; Day already caps at 15. A cap (e.g. the 15 highest-priority visible rows animate; the rest render the static frame with glow) changes behavior for extreme lists and therefore needs explicit sign-off. Strongly recommended: without *some* bound the worst case stays unbounded no matter how efficient each frame is.
- **Gated experiments — not defaults (Phase 5):**
  - *Encode format:* `WEBP_LOSSY` (API 30+ only; PNG below) — benchmark encode/decode time and run golden-screenshot comparison; lossy WebP can artifact text edges. Fallback: PNG with lower compression level, or `WEBP_LOSSLESS`.
  - *Render scale:* the current 1.15× is **supersampling** (quality feature, not a bug). Compare 1.15 vs 1.0 side-by-side before changing anything.
  - *Parallel frame rendering:* bounded concurrency only (2–3 workers, one in-flight bitmap each — hundreds of concurrent ARGB_8888 frames would spike memory); per-key uniqueness via unique temp-file names (already `.tmp`+rename) rather than the global lock. Benchmark against sequential.

### 4.3 Interactive transitions: keep the choreography; make its frames cheap

Launcher `ListView`s do not animate adapter diffs, so the app-driven frame-push is the *only* way these transitions can animate — it stays. Fixes:
- One data load per interaction (derive before/after row sets from a single snapshot; delete the duplicate load in `COLLECTION_ACTION_TOGGLE_SUBTASKS`).
- Transition frames already suppress priority motion; once static rows are native (Phase 4), a frame is a few KB of `setViewLayoutHeight`/alpha attributes — delete `WIDGET_TASK_TRANSITION_BITMAP_SCALE` and the 0.78× blur path (quality improvement, sanctioned).
- Optimistic first frame from `KgsWidgetCollectionRowsCache` before any data load.
- Keep `KgsWidgetInteractionTokens` latest-wins cancellation. If a transition still stutters after Phases 1–4, report measurements and escalate — downgrading an animation to one-shot is reserved to the owner.

### 4.4 Update scheduling

Per-widget serialized queues in `KgsWidgetUpdateScheduler`: one in-flight update per (kind, widgetId), newest-pending-wins coalescing for generic refreshes, and navigation/interaction jobs take precedence over background refreshes (a nav tap cancels a queued background refresh, not vice versa). This removes the observed overlapping Month renders.

### 4.5 The ContentProvider store stays

Animated frames and Day grid art keep flowing through `KgsWidgetBitmapUriStore`/`KgsWidgetImageProvider` (aggregate size exceeds the RemoteViews inline-bitmap cap — the `isRemoteViewsBitmapMemoryError` fallback exists for this). Small static bitmaps go inline; Month stops producing bitmaps if/when its native conversion lands. **Do not delete `files/widget_images` or the provider.**

---

## 5. Implementation plan (measurement-first)

> Every phase ships independently. Bump the relevant `WIDGET_*_RENDER_SIGNATURE_VERSION` whenever visuals or signature inputs change.

### Phase 0 — Instrumentation (small)
Structured, greppable timing/counter logs behind `WidgetLog` for every update: update cause (nav / interaction / sync / periodic / resize / boot), concurrent-job count at start, data-load ms, rows built, RemoteViews build ms, cache hits vs misses, bitmaps rendered, encodes, file writes, Binder apply ms, total ms. Capture baselines per widget kind on a real mid-range device, including the known references: Month no-op ≈ 1.62 s, Tasks no-op ≈ 47 ms. Add golden screenshots (all widgets, 2 sizes, light+dark) for later comparisons.

### Phase 1 — Stop doing unnecessary work; zero visual change (medium)
1. **Month signature before render:** compute `monthRenderSignature` from `(today, settings, palette, size, renderSpec, page)` *before* `renderMonthRoot`; on match, skip the entire build. Same for the Multi month section. Expected to turn the 1.62 s no-op into tens of ms.
2. **Reuse loaded snapshots:** thread the `collectionSnapshot` rows from `KgsWidgetUpdater.update()` into `renderCollectionWidget()` instead of the second `listRows` call. Same for signature computation inside interactions.
3. **Scheduler coalescing + serialization + precedence** per §4.4.
4. **Lazy cache lookup:** `getIfPresent` + `bitmapProvider` lambda in `setWidgetRowImage` / `setDayGridImage` / `setMonthSpanChipImage` — no bitmap creation, hashing, or touch on a hit beyond a single existence check.
5. **Prune overhaul:** per-update `beginUpdate`/`endUpdate`, generation-protected eviction, cap raised and derived from actual per-widget frame counts; drop per-put `listFiles`.
6. **Single data load per interaction** (§4.3 bullet 1) — choreography untouched.
- **Acceptance:** unchanged-data update of every widget kind performs 0 renders / 0 encodes / 0 writes and < 100 ms total; two rapid `updateAll` calls produce one render per widget; side-by-side recordings of all animations are indistinguishable from baseline.

### Phase 2 — Re-measure and decide (checkpoint, small)
Re-run Phase-0 measurements. Decision gates:
- If Month changed-data updates are now acceptable (< ~300 ms build), the native Month conversion (Phase 4a) may be deprioritized or descoped to chips only.
- Record cold-render costs for animated rows (first render after resize/theme change) — this sets how much Phase 5 experiments matter.
- Present the animation-row-cap question to the owner with real numbers (frames rendered/encoded for their actual task lists).

### Phase 3 — Prototypes with gates (medium)
Build three throwaway prototypes; each must pass an overlay-screenshot comparison and run on API 26 and 31+:
1. **One native Month chip** (span 1 and span 3, with continuation fade ends and corner variants) vs the current bitmap chip.
2. **One native static task row** (depth 2, with hierarchy lines, status icon, chevron) using the pre-S `ImageView`-mask tint strategy and the S+ path.
3. **Collection transport comparison:** direct `RemoteCollectionItems` vs the service-backed lazy factory for a Tasks widget with 150 rows including animated ones — measure build time, Binder payload, launcher behavior, memory. Choose the transport per widget kind based on data (platform docs warn eager `RemoteCollectionItems` is unsuitable for bitmap-heavy collections; the service path already exists and may become the default even on S+).
- **Acceptance:** go/no-go per conversion recorded in this doc with screenshots and numbers.

### Phase 4 — Conversions (large; only what Phases 2–3 justify)
- **4a Month native:** native chips/cells per prototype; keep `WidgetMonthNavigation`, page cache, skeleton flow, resize debounce, `widget_month_flipper` page swap. No fixed tree-size reduction target — measure build ms and Binder payload instead.
- **4b Static rows native (Tasks/Agenda/Multi/Day list rows):** per prototype; animated rows keep the flip-book; native and bitmap geometries must align pixel-for-pixel (`TaskCardBaseSpec` as single source of truth). Delete `taskRowBackgroundBitmap`, `agendaEventCardBitmap`, icon bitmaps, and the 0.78× transition path when nothing references them.
- **4c Transition slimming** per §4.3 (falls out of 4b).
- Add `?android:attr/selectableItemBackground` press feedback on clickable rows/buttons.
- **Acceptance:** Phase-2 targets met on changed-data updates; overlay screenshots approved; behavioral checklist (§6.3) passes on both API branches.

### Phase 5 — Benchmarked experiments (small each, independent)
Run each with before/after numbers and golden-screenshot comparison; adopt only on a win:
1. WEBP encode for animated frames (API ≥ 30 branch; PNG below).
2. 1.0× exact-pixel vs 1.15× supersampled frame rendering.
3. Bounded-parallel frame rendering (2–3 workers) vs sequential.

### Phase 6 — Hygiene (small)
- Small static bitmaps inline; Month store traffic = 0 (post-4a); one-time cleanup of orphaned/stale-format cache files on upgrade.
- Remove dead `ACTION_CONFIGURATION_CHANGED` manifest filters; dark-mode responsiveness via existing triggers or a runtime-registered receiver.
- Audit `updateAll` call sites (`CalendarViewModel.kt:532-616`, `AppGraph.kt`, `KgsCalendarApplication.kt`) to target only affected kinds (scheduler coalescing from Phase 1 already blunts the cost).

### Explicitly rejected alternative: Jetpack Glance
Same RemoteViews underneath, no custom canvas (hierarchy lines, timeline art, animated frames), full rewrite cost, no additional headroom. Not recommended.

---

## 6. Verification plan

1. **Log-based hard targets** (mid-range device):
   - Unchanged-data update, any widget: 0 renders / 0 encodes / 0 writes; Month no-op < 100 ms (baseline 1.62 s).
   - Changed-data Tasks/Agenda update with warm frame cache: < 100 ms app-side; cold (post-resize/theme): bounded and paid once per state — record actual numbers at Phase 2 rather than guessing.
   - Interaction tap: exactly 1 data load; first visual response (optimistic frame or ripple) < 100 ms.
   - No overlapping renders of the same widget in logs.
2. **Animation fidelity:** side-by-side recordings (baseline vs each phase) of priority motion (P1/P3/P5), subtask expand/collapse, sort morph, all-day expansion, month prev/next. Priority motion frame-identical; transitions same choreography at equal-or-better frame rate; no blur dip (0.78× removal is sanctioned).
3. **Behavioral checklist per widget** (must match current features): Tasks — sort cycle, create (+), row open, status toggle, subtask expand/collapse, display/create/subtask-default modes, theme modes; Agenda/Multi — sections, span rows, month part nav (multi), open/create; Month — prev/next/today, chip taps open day, resize (dot cells ↔ text cells), first-day-of-week, hidden collections; Day — prev/next/today, all-day expand, hour-grid taps create events at that hour, now-line, scale %, start hour; all — light/dark/system theme, locale change, completed-task visibility.
4. **Visual QA:** overlay screenshots current vs new, 2–3 sizes per kind, light + dark; only sanctioned deltas.
5. **Device matrix:** API 26–30 device/emulator (service-factory path, PNG encode, `ImageView`-mask tinting) and API 31+ (direct collections if kept, `setColorStateList`).
6. **Upgrade test:** current build → new build with all 5 widgets placed; widgets refresh without re-adding; stale cache files cleaned; `files/widget_images` still present and functional for animated frames.

---

## 7. Key code references

- Update orchestration + signature checks: `KgsWidgetUpdater` — `KgsWidgetProvider.kt:1077`; month signature applied late `:1146-1156`
- Month render-before-signature: `renderMonthWidget` `:3594` → `renderMonthPageResult` `:3609` → `renderMonthRoot` `:3706`
- Double row-load: snapshot `:1113` vs `renderCollectionWidget` `:4364` (`listRows` at `:4407`)
- Tap handling: `onReceive` `:238`; `KgsWidgetActionReceiver` `:492` (duplicate before/after loads `:540-556`)
- Frame-push drivers: sort morph `:705`, subtask toggle `:790`, all-day expansion `:1378`
- Flip-book binding: rows `:5975-6023`; day grid `:1629-1685`; frame painter `:6367`; effect math `WidgetTaskCardRenderer.kt:112-138`
- Art width derivation: `collectionArtWidthDp` `:6903` (360 dp = fallback only, `WidgetCollectionRenderOptions` default)
- Day-only animation cap: `WIDGET_PRIORITY_MOTION_ITEM_LIMIT` `:120` → used only via `WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS` `:3038`
- Month tree build + chips: `:3706-3902`, `monthSpanChipBitmap` `:5629`
- Bitmap store: `WidgetBitmapCache.kt` (`put` renders-before-check, per-put prune, 320 cap); provider: `KgsWidgetImageProvider.kt`
- Collection factories (lazy path): `:4674-4819`; data source: `:4821`
- Scheduler: `WidgetUpdateScheduler.kt`; update triggers: `CalendarViewModel.kt:532-616`, `KgsCalendarApplication.kt`
- Manifest: `AndroidManifest.xml:32-130`; AppWidget info: `res/xml/widget_*.xml`; `minSdk = 26` (`app/build.gradle.kts:28`)

---

## 8. Implementation record (2026-07-11)

### Adopted

- Structured `WidgetPerf` update metrics with update cause, concurrency, data/build/Binder timings, and bitmap/cache counters.
- Signature-before-render for Month and the combined Month+agenda state in Multi.
- Prepared collection snapshots reused by rendering and subtask interactions; duplicate row loads removed.
- Per-widget serialized, newest-pending-wins scheduler with navigation/interaction precedence. `SyncWorker` now awaits this scheduler instead of bypassing it.
- Lazy bitmap providers, generation-aware cache retention, one prune per update, per-widget locking, and unique temporary files.
- Native Month span chips using native text and nine tinted alpha-mask edge variants. Month chip cache traffic is now zero.
- Dead manifest `CONFIGURATION_CHANGED` filters removed; runtime `Application.onConfigurationChanged` remains the active configuration trigger.

### Device results

Measured on the connected Samsung API 36 device with Month widget 98 and Tasks widget 99:

| Scenario | Before | After |
|---|---:|---:|
| Month unchanged, stable generation | ~1,620 ms | 85 ms |
| Month navigation | 357-543 ms | 117-161 ms |
| Tasks unchanged | ~47 ms | 28-55 ms |
| Tasks warm changed update | not instrumented | 96-137 ms, 20 cache hits, 0 renders |

Rapid Month navigation accepted three taps 90 ms apart, advanced all three months, kept `concurrent=1`, and produced matching final header/content. Native Month screenshots preserve lane geometry, colors, continuation fades, corners, and click targets while rendering text more crisply. Navigation now prefers an exact-generation page, falls back to the latest complete page in the same model namespace, and retains the old complete month on a total miss until one atomic target update is ready. Device logs confirmed no `complete=false` navigation renders: a cold miss produced one complete update, and the next prewarmed month completed in 150 ms. After the debounced post-sync update, unchanged refreshes hit the page cache (`pageMs=0`).

### Gate decisions

- **Static task/event row conversion: deferred.** The available API 36 device cannot satisfy the required API 26 and API 31+ screenshot gate. Task hierarchy branches also share exact geometry with the protected moving-card frames, so converting without the lower-API overlay check would violate the zero-regression constraint.
- **Collection transport switch: not adopted.** The connected widgets do not contain the required 150-row bitmap-heavy dataset, so the direct-vs-service comparison would not be representative.
- **WebP, 1.0x rendering, bounded parallel rendering: not adopted.** Warm-cache updates now perform zero frame rendering. There is no measured win that justifies changing frame bytes, supersampling quality, or memory concurrency.
- **Animation-row cap: not adopted.** This remains an owner-visible behavior change and no sign-off was given; all existing priority motion remains intact.

### Verification

- `:app:testDebugUnitTest`: 179 tests, 0 failures, 0 errors.
- `:app:assembleDebug`: successful on JDK 17.
- APK installed over `com.kgs501.kgscalendar.debug`; existing widgets refreshed without re-adding.
- API 26 visual/launcher verification remains an explicit device-matrix gap for any future static-row or transport conversion.

---

## 9. Constraints for implementers

- **Zero feature and design regressions — binding.** Priority motion (glow + lift + scale + P1 shake, card content moving as one unit) preserved exactly on Tasks/Agenda/Day/Multi; transition choreography preserved. Sanctioned visual deltas only: crisper native text, no 0.78× transition blur, no `RGB_565` banding. Anything else — including an animation-row cap for Tasks/Agenda/Multi, transport changes visible in behavior, encode-format artifacts, or downgrading any animation — requires explicit owner sign-off; report measurements and stop rather than deciding unilaterally.
- **Measurement discipline:** no conversion (Phase 4) or experiment adoption (Phase 5) without the Phase-0 instrumentation numbers and screenshot gates recorded in this document.
- **API branches:** minSdk 26. Direct `RemoteCollectionItems`, `setColorStateList`, `setViewLayoutHeight/Width/Margin` are S+ (31+); `WEBP_LOSSY` is 30+. Every native-rendering change needs an explicit pre-S strategy, tested.
- Bump `WIDGET_MONTH_RENDER_SIGNATURE_VERSION`, `WIDGET_DAY_RENDER_SIGNATURE_VERSION`, `WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION` whenever visuals or signature inputs change.
- `KgsWidgetProvider.kt` is 7.5k lines; split as you touch each area (suggested: `updater/`, `render/tasks`, `render/month`, `render/day`, `data/`), keeping refactors within the phase being implemented.
- The app targets phones; test launcher grid resizing — all sizing flows through `WidgetSize.from()` (`WidgetModels.kt:35`) and `collectionArtWidthDp()`.
