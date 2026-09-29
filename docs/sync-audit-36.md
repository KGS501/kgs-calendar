# Sync reliability audit — issue #36

Issue: https://github.com/KGS501/kgs-calendar/issues/36

Scope: V.1.4.0-2 sync scheduling, local outbox, CalDAV discovery and reconciliation,
read-only subscriptions, Android CalendarProvider, error reporting and recovery.
Implemented for V.1.4.0-3. The verification below records the pre-release audit.

## Triage

| Report | Finding |
| --- | --- |
| No configurable sync frequency | Confirmed. The app hard-coded a 15-minute periodic WorkManager job. |
| Server edits remain invisible until manual sync | Plausible from scheduling, and reproducible under app-side failure conditions. Refresh-on-resume was throttled for ten minutes, enqueueing counted as sync activity, and there was no polling while the app stayed open. A failed calendar also prevented later calendars in its account from downloading changes. |
| Local edits never trigger immediate upload | Too broad: UI, widget and notification paths already attempted direct uploads. However, those attempts had no durable one-time recovery job. Failed/interrupted uploads could wait for a periodic/manual sync, and queue races could leave them conflicted indefinitely. |
| Is this user error? | The reproduced defects do not require user error or unusual configuration. The issue contains no device logs, so its particular 20-minute delay cannot be attributed conclusively. Android power/network restrictions can also delay scheduled work. |

## Reproduced defects

Six initial regression tests were run against the unchanged sync implementation. All six
failed at the intended assertions:

1. Failure of the first calendar prevented a later task calendar from refreshing.
2. A transient resource download failure was treated as success, including when
   another account was healthy. No retry was requested.
3. Deleting an item while its PUT was in flight retained the old ETag on DELETE;
   the app then conflicted with its own successful upload.
4. An upload snapshot sent an obsolete PUT even after the user had replaced it
   with a deletion while an earlier request was running.
5. A remote timed-to-all-day task change was overridden by cached local times.
6. A missing remote calendar caused unsent local work to be deleted.

A seventh reproduction subsequently confirmed that two different servers exposing
identical DAV paths overwrite each other’s local cache entries. The test also
checks that an edit to the second account uploads only to that server.

## Implemented behavior

### Scheduling and recovery

- A persisted background interval offers 15, 30, 60, 120, 360, 720 or 1440 minutes;
  the default remains 15. Updating it updates the existing periodic job.
- Process foregrounding starts a one-minute refresh loop; backgrounding stops it.
  Only a completed full sync updates the freshness timestamp. Upload-only work
  does not hide remote changes by marking downloads fresh.
- A process-level Room outbox observer schedules uploads for committed changes
  from all entry points, and for pending changes found after process restart.
  A short debounce coalesces bursts. Existing direct uploads remain the fast path.
- Upload jobs append rather than cancelling an upload in progress. A failed or
  cancelled old work chain cannot prevent a new edit from scheduling recovery.
  Android 12+ requests expedited execution, falling back to normal work when its
  quota is exhausted. All jobs require connectivity.
- The recovery worker drains all enabled-calendar pending changes, not a wall-clock
  window from the most recent UI action. Empty recovery jobs make no HTTP requests.
- Transient failures use bounded WorkManager backoff. The periodic job remains a
  later recovery opportunity. Authentication and real conflicts are surfaced,
  not overwritten or endlessly retried within a single job.

### Reconciliation and preservation

- Colliding DAV paths from different servers receive distinct absolute collection
  and resource identities. Existing identities remain stable across later discovery.
  An already-connected identical absolute calendar is rejected rather than reassigned.
  Changing an account’s server/login is serialized with sync, requires an empty
  outbox, and clears the old remote cache and cursors before reconnecting. Password
  or display-name updates preserve the existing cache.
- Older caches undergo one full reconciliation per enabled collection, including
  unchanged ETags. The completion marker is committed with a successful batch and
  preserved across discovery. Later refreshes return to incremental operation.
- Accounts and collections fail independently. Successful work is retained while
  errors are aggregated; suppressed transient errors still qualify for retry.
- Failed resource downloads retain their old sync cursor and now report failure.
  An invalid sync token forces a full listing even if the ctag is unchanged.
- A missing ETag property is distinct from a deleted resource in WebDAV responses.
  Failed/empty malformed listings cannot be interpreted as an empty calendar.
- Downloaded data uses the ETag returned with that same body. A separate later
  ETag lookup must not certify an older body as current.
- Each upload re-reads its queued mutation before sending it. A PUT acknowledgement
  advances both newer PUT and DELETE preconditions. DELETE acknowledgement preserves
  a concurrent restore/edit rather than erasing it.
- A rejected PUT is acknowledged only if a GET confirms the exact queued content
  already exists remotely, covering lost replies. Different content stays a conflict.
- DELETE without a known ETag first verifies absence or matching remote content;
  it never blindly removes an independently changed server item.
- Missing calendars retain unsent work. Legacy heuristics that deleted pending edits
  based on cache mismatch or age were removed. Duplicate-UID repair is restricted
  to equivalent escaped/unescaped paths and preserves the queued precondition;
  distinct resources are not automatically merged/overwritten.
- Server task dates/times and write permissions are authoritative when there is no
  pending local mutation. Local display color overrides remain local.
- Read-only feeds use ETag/Last-Modified conditional GETs, retain cached data on
  failures, and report errors for retry. Invalid non-calendar bodies do not erase
  the cache. HTTP validators are used only for the URL that produced them.
- Cancellation propagates through upload and source engines. Network/provider
  calls remain outside Room transactions; remote operations share the existing lock.

## Boundaries and tradeoffs

- This is polling, not a promise of real-time server push. WorkManager periodic
  work has a 15-minute minimum and Android may defer it for power restrictions.
  Force-stopping an app also prevents background execution until it is reopened.
- The one-minute foreground check balances freshness and battery/network cost.
  CalDAV retains incremental sync, ETag comparison and batched multiget. Discovery
  remains per full sync so permission, calendar and account changes are noticed.
- Android device calendars are mirrored through CalendarProvider; the owning
  account's sync adapter controls their network schedule. KGS does not take over
  another app's account credentials or background policy.
- A true concurrent-edit conflict preserves local and remote versions and remains
  visible in the existing Problems UI. Automatic last-writer-wins merging would
  reduce visible conflicts by risking information loss, so it is not used.
- The issue reporter's exact server, device power state and network history were
  not supplied. Regression fixtures establish app defects, not the cause of every
  observed delay. Real-world background timing still needs device feedback.

## Verification

`SyncReliabilityRepositoryTest` covers the six reproduced failures plus malformed
listings, property-vs-resource 404, invalid tokens, lost acknowledgements,
conditional read-only refresh, account isolation, one-time cache reconciliation,
account-binding changes, and preservation of legacy queued edits/duplicate UIDs.
`SyncSchedulingTest` exercises real WorkManager enqueue policies, connectivity
constraints, interval updates and refresh coalescing. Settings tests reopen DataStore
and verify persistence.

Verified on 2026-09-29:

- All 811 unit tests passed: 456 app and 355 data tests; no failures or skips.
- The data suite included two opt-in tests against a disposable Nextcloud 34.0.0
  server. Task completion uploaded successfully; external task title/status and
  timed-to-all-day changes downloaded successfully. External/app trash, restore
  and permanent deletion also passed against the actual server.
- The disposable server and its temporary credentials were removed afterward.
- Debug app and instrumentation APK builds passed.
- Both `AutomaticSyncInstrumentedTest` tests passed on the project API 36 emulator
  (104 seconds). The real app/WorkManager retried a simulated HTTP 503 upload,
  downloaded a server edit while the app stayed open without manual sync, and
  updated periodic work from the saved interval preference.
- An initial emulator attempt timed out during startup alongside Android System UI;
  after reducing emulator background load, the complete test run passed. No app
  changes were needed for that retry.
- Manual UI checks passed: all seven interval choices in portrait, selection updates
  the settings page, and landscape scrolling reaches the 24-hour choice. The
  original interval, display size and Google Play services state were restored;
  the emulator was stopped.
- `git diff --check` passed. Version metadata remains V.1.4.0-2 / code 29.
  No commit, push, PR, issue-state change or publication was performed.

Protocol/platform references:
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- https://www.rfc-editor.org/rfc/rfc6578
