# V.1.4.0-3 DNS warning cascade

## Report and triage

The phone reported hundreds of event-level `Import failed: Unable to resolve host`
messages during a manual sync. The user could open Nextcloud in the phone browser;
a second manual sync cleared the warnings and retained the populated server trash.
A read-only DNS lookup and public Nextcloud status request from this development
machine also succeeded (Nextcloud 34.0.0, not in maintenance).

This does not establish user error or a permanent server configuration problem.
It is consistent with a temporary resolver/connectivity failure during a sync.
Browser success does not establish that the app had successful DNS resolution at
the exact failed request. The phone's historical network/DNS state is unavailable,
so the underlying trigger cannot be determined from these screenshots.

No DNS resolver configuration changed between V.1.4.0-2 and V.1.4.0-3. The older
engine already caught failed batch requests and fell back to individual GETs, then
persisted those connection failures as resource import errors. V.1.4.0-3's one-time
cache reconciliation expanded the number of resources that could encounter this
behavior. The precise 368-item incident cannot be replayed without the device state;
the app-side failure mechanism is reproducible independently.

## Reproductions against released behavior

Controlled OkHttp DNS failures (real resolver callback, no pooled connections):

- A 60-event upgrade refresh made **63 failing lookups** after DNS became unavailable.
- Ten pending uploads made **ten failing lookups** rather than stopping that account.
- A fallback GET failure kept the old cached body but associated it with the newly
  listed server ETag, and wrote an event-level DNS import error.

An initial legacy-warning fixture incorrectly placed errors after an already
acknowledged cursor. It was corrected to represent the actual failed-download state:
newer server revisions with the previous local cursor retained. It is a recovery
check, not evidence of an additional reproduced defect.

## Changes

- Connection failures abort the current account's network work. Other accounts
  still run; an unrelated healthy feed is covered by the regression fixture.
- Transient batch failures do not trigger per-item GET storms. Servers that do not
  implement a REPORT can still use the fallback (including HTTP 501).
- Retryable fetch failures preserve the complete cached resource, including its
  existing ETag, and hold the collection cursor for retry. They are reported at
  source level rather than as broken event imports.
- Uploads remain queued on connection loss without adding DNS warnings to every
  resource. The source records the connection problem for both full and upload-only
  sync. Genuine conflicts and malformed calendar data retain their item-level errors.
- Missing-ETag repair also stops on connection loss and propagates cancellation.
- Successful retries clear previously persisted import/DNS warnings by downloading
  the server data normally. No blanket clearing, cache deletion or forced upload of
  cached server events is used.

## Test infrastructure

[Nextcloud testing](nextcloud-testing.md) documents the new disposable local runner.
It covers real DAV/trash protocol behavior without needing a separate machine or
production credentials. A shared VM is an optional complement for physical-device
TLS, reverse-proxy, Private DNS and background-network tests.

## Verification

Verified on 2026-09-29:

- The focused repository suite passed after the fix.
- All six outage regressions passed, including the independent healthy account,
  missing-ETag repair and HTTP 503 batch handling.
- All three live Nextcloud tests passed twice on freshly created 34.0.0 servers:
  task synchronization, server trash operations, and DNS loss/recovery. None were
  skipped in these live runs. The runner's forced-test option was exercised.
- The final `testDebugUnitTest :app:assembleDebug` run passed: 463 app unit tests
  retained their passing results and 359 data unit tests passed; the three opt-in
  live tests were skipped in this final server-free run, having passed above.
- One full live run encountered two intermittent existing test failures (a
  MockWebServer shutdown timeout and an unexpected GET assertion). Both passed on
  focused rerun and in the final standard suite, without production-code changes.
  The GET assertion now includes request paths to help diagnose any recurrence.
- Debug APK build and `git diff --check` passed. This data-layer change was verified
  with repository/live-server tests; no new emulator UI test is claimed.
- Both disposable servers, their anonymous volumes and temporary credentials were
  removed. No production credentials or calendar contents were used.

Logs: `/tmp/kgs-dns-baseline.log`, `/tmp/kgs-dns-fixed.log`,
`/tmp/kgs-nextcloud-outage-final.log`, `/tmp/kgs-dns-recheck.log`, and
`/tmp/kgs-dns-final-check.log`. Live-test XML evidence from the second full live run
is retained in `/tmp/kgs-dns-full-evidence/`.

Prepared for V.1.4.0-4 (version code 31). The verification above was completed
before release packaging.
