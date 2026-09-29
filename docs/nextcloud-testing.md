# Nextcloud integration testing

Run from the repository root on this development machine:

```sh
python3 tools/test_nextcloud.py
# Or run every debug unit test with the real-server checks enabled:
python3 tools/test_nextcloud.py --all-tests
```

The runner uses a disposable `nextcloud:34.0.0-apache` container, matching the server
version used for this investigation. `--image nextcloud:<version>-apache` selects
another explicit server version when testing upgrades. Docker must be available;
on this machine the runner can use the existing non-interactive sudo access.
Do not run another Gradle invocation concurrently.
The runner forces test execution for each new server instead of accepting cached
test results; compilation caches remain enabled.

The server binds only to a random loopback port, has generated temporary credentials,
and is limited to one CPU and 1 GiB RAM. SQLite is sufficient for these small protocol
fixtures. The runner removes its own container, anonymous volume and credential file
in `finally`, including when tests fail. It does not connect to a production account.
An abrupt machine/process kill can bypass cleanup; any leftover containers carry a
`kgs.calendar.test-owner` label and a unique `kgs-calendar-test-` name.

The live tests exercise the app's actual repository, HTTP client, iCalendar codec
and Room persistence against Nextcloud:

- Local task completion uploads, and external task edits download.
- External and app deletions, trash listing, restoration and permanent deletion.
- DNS loss between listing and multiget, with unchanged cache/cursors during the
  failure and successful recovery of newer server data and old DNS warnings.

The outage test controls OkHttp's DNS implementation and disables pooled connections;
the DAV server and responses are real. This makes the failure repeatable without
changing host DNS or disrupting other projects. Separate fast MockWebServer tests
cover larger collections, batch boundaries and pending-upload preservation.

These are DAV integration tests, not browser automation of the Nextcloud Calendar
web app. The server provides the DAV/trash endpoints; Calendar 6.5.2's browser UI is
not installed or claimed as covered. TLS, Android Private DNS, Wi-Fi changes and
background scheduling on physical phones remain separate device-test concerns.

A local disposable server is the default for deterministic regression checks. An
always-on VM (including a VM on Proxmox) is useful if physical devices need a stable
shared test URL, HTTPS, reverse proxy and realistic background/network scenarios.
It can supplement this runner; it is not required for the automated protocol tests.

Container configuration reference: https://github.com/nextcloud/docker
