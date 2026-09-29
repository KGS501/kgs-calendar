# Full-day section audit — September/October overlap

Scope: timeline full-day events and date-only tasks, compact/expanded row allocation,
viewport participation, overflow, animation, identity, interaction and rendering cost.
Implemented for V.1.4.0-3 alongside the earlier sync, trash and timeline fixes.

## Reproduction and behavior

The original collapsed algorithm selected items independently for each day, then
packed visible fragments into rows. It forgot that two adjacent fragments could
belong to events whose original ranges overlap. A continuation gradient from the
second event could paint over the first, making two different events look continuous.

With five events, a four-row setting, and events 3/4 overlapping on October 1:

| Visible window | Collapsed behavior |
| --- | --- |
| September 29–October 1 | Rows 1–3 contain events 1, 2, 3; row 4 abstracts 4 and 5. |
| September 30–October 2 | Row 3 shows event 3, an October 1 abstraction containing 3/4, then event 4; row 4 abstracts event 5. |
| October 1–3 | Rows 1–3 contain events 1, 2, 4; row 4 abstracts 3 and 5. |

A collision is activated only when visible pieces exist on both sides of the overlap.
Off-screen dates and render bleed cannot activate it. Multiple overlap days receive
individual controls. Adjacent date ranges that do not overlap do not receive one.
Each hidden item belongs to exactly one abstraction on a given day.

## Findings and changes

- **Collision representation:** explicit per-day/per-row groups reserve the overlap
  cells. Card fragments retract out of these cells; ordinary overflow contains only
  the remaining hidden items. Expanding either control reveals the full layout.
- **Ordering:** already-visible continuing items retain priority. Within that tier,
  later end dates precede earlier ones; start date, title and stable resource identity
  break ties deterministically. Incoming events do not evict an outgoing day merely
  because the pager crossed its midpoint. Height and rendering use the same priority
  window during partially completed swipes.
- **Row budget:** whole-fragment greedy packing can exhaust rows even when each day
  fits. The allocator first seeks a continuous free row; if necessary, it splits at
  occupied/reserved cells instead of exceeding the budget or covering an overflow
  control. Property-style tests check budgets, cell occupancy and item conservation.
- **Identity:** UIDs are not globally unique across calendars. UI identity now uses
  resource href plus occurrence identity for events and tasks. Copies no longer share animation
  state or Compose keys. Event/task completion and drag behavior retain their existing
  callbacks and resource identities.
- **Animation:** retraction animates date endpoints rather than absolute pixel
  positions, so horizontal gestures remain attached to the pager. Gradient masks
  blend cards into hidden intervals. Existing leading viewport fades remain intact.
  Overlap controls fade/scale in and remain composed through fade-out; expansion
  uses the existing shared card frame and lane interpolation.
- **Style/accessibility:** existing card/status colors, corner styling, overflow
  colors and motion constants are reused. Collision controls leave room for the
  adjoining continuation gradients. Overflow controls now announce hidden item
  counts and titles in English/German rather than only three dots.
- **Long tasks:** the all-day overlay now reads start/end bounds directly instead
  of allocating a date list capped at 370 days, which could hide long-running tasks.
- **Efficiency:** sort day candidates once per scene; cache continuation lookups by
  item; order visual pieces when building the scene, not on every animation frame.
  Compact row occupancy is bounded by visible days and the configured row count.
  Extra compositing is limited to moving edges blending into an abstraction; settled cards use the existing continuation gradient. No new timer,
  polling job or background work is introduced.

## Assessment and remaining limits

The scene/model split and shared expansion frame are useful foundations and were
retained. Explicit collision cells and stable identities make their invariants
reviewable and testable. Scene calculations remain memoized across pixel-only
scrolling; viewport geometry still updates as the pager moves.

Expanded lane assignment still scans previously assigned intervals and can have
quadratic worst-case cost for very dense calendars. This audit does not claim a
benchmark result for thousands of simultaneous items or every physical device.
Color-coded continuation gradients supplement the explicit overlap control; the
control remains the unambiguous indicator when adjacent events share a color.

## Verification

Before implementation, targeted tests reproduced the missing overlap, duplicate
resource identity, and duration-versus-end ordering problems. The two edge windows
passed unchanged. The long-task case also failed against the prior date enumeration.

Verification on 2026-09-29:

- `testDebugUnitTest` passed: all 463 app tests ran successfully; 355 unchanged data
  tests reused their passing Gradle results from the sync verification.
- The new layout suite includes 1,050 generated combinations (150 fixtures × seven
  row limits), checking row budgets, unique item representation and cell occupancy.
- Debug app and instrumentation APK builds passed.
- Eleven emulator tests passed: the three new collision/animation tests, existing
  expansion transition test and seven all-day drag tests. After final gradient
  polish, the four collision/transition tests passed again, including an additional
  run with optional frame export enabled (`-e captureAllDay true`).
- Device checks cover opening the collision control, separate expanded lanes,
  intermediate retraction geometry, reversing a swipe and keeping the collision
  until the outgoing day's last pixel leaves the viewport. Light/dark and expanded
  captures were inspected. A direct device screenshot confirmed all titles and
  the settled overlap gradients; the single collision/expansion test also passed
  during that capture.
- Emulator startup initially failed once, then succeeded after settling. Compose's
  screenshot API also timed out once and omitted parts of titles in its initial
  light-theme image; direct device capture showed the titles correctly. Frame
  export is optional and retries capture timeouts, so ordinary regression tests
  do not depend on screenshot I/O. These capture artifacts are not counted as
  app layout failures.

Local verification logs are in `/tmp/kgs-allday-*.log`; inspected images are in
`/tmp/kgs-allday-verification/` (the direct collapsed device frame is
`direct/05.png`). These temporary artifacts are not release assets.

Compose animation lifecycle reference:
https://developer.android.com/develop/ui/compose/animation/composables-modifiers
