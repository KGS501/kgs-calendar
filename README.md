# KGS Calendar <a href="https://play.google.com/store/apps/details?id=com.kgs501.kgscalendar"><img alt="Get KGS Calendar on Google Play" src="https://img.shields.io/badge/Get_it_on-Google_Play-414141?style=for-the-badge&logo=googleplay&logoColor=white"></a>

<img width="1794" height="876" alt="KGS Calendar screenshot" src="https://github.com/user-attachments/assets/b2c5feb6-cbca-4330-bf6d-fe05e6e2ead8" />

KGS Calendar is an Android calendar and task app for managing events, tasks, reminders, and synchronized calendar sources. It can be used offline with local app data, with Android device calendars, or with CalDAV/Nextcloud calendars and task lists.

If you like the App and want to support me continuing the development, consider sponsoring the project!

<a href="https://github.com/sponsors/KGS501"><img height="56" alt="Sponsor KGS501 on GitHub" src="https://img.shields.io/badge/Sponsor-KGS501-EA4AAA?style=for-the-badge&amp;logo=githubsponsors&amp;logoColor=white"></a>

## Available on Google Play Store

Get automatic updates and don't risk downloading any unstable pre-release versions

<a href="https://play.google.com/store/apps/details?id=com.kgs501.kgscalendar"><img height="56" alt="Get KGS Calendar on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"></a>

## Features

- Events and tasks in one app
- Agenda, day, 3-day, month, and task-list views
- Local calendar support without a server
- Android device calendar access when permission is granted
- CalDAV and Nextcloud calendar and task sync
- Read-only ICS and CalDAV calendar subscriptions
- Open or share ICS attachments from email and file apps, review the events, and import them into a local or CalDAV calendar
- Reminders for events and tasks
- Recurring events and tasks
- Search across events and tasks
- Calendar colors, ordering, and visibility controls
- Optional location search through OpenStreetMap Nominatim
- Optional map previews using OpenStreetMap tiles
- Light and dark themes
- Home screen widget support
- English and German app language support

## Windows desktop app (preview)

`:desktopApp` runs the Android app's own screens, view model, sync engine and database on
Windows with Compose Multiplatform, so the desktop app looks and behaves like the Android app
instead of re-implementing it.

- `:core:data` is multiplatform (Android and desktop JVM). Android-only parts (device calendars,
  encrypted credentials, image scaling) sit behind small platform functions; the desktop keeps
  passwords encrypted with Windows DPAPI and has no device calendars.
- `:shared:app` holds the screens, view model and helpers formerly in `:app`. Platform
  differences live in `ui/platform` (`expect`/`actual`): back handling (Escape and the mouse's
  back button on the desktop), file pickers, links and maps, system bars, clock updates, window
  metrics, shortcuts and mouse-drag scrolling.
- `shared/strings/res` is the single source of the strings. Android merges it into its
  resources; `:shared:app` generates `SharedR` (imported as `R`) and the string tables from it.
- `:app` keeps what only Android has: activities, widgets, notifications, WorkManager sync and
  the calendar provider.

Run it with JDK 17 from the repository root (the Android SDK is still needed to configure the
build). Use a separate profile and synthetic items for testing:

```
KGS_DESKTOP_DATA_DIR=<empty folder> KGS_DESKTOP_DEMO=1 ./gradlew :desktopApp:run
./gradlew :desktopApp:createDistributable   # portable app in desktopApp/build/compose/binaries
./gradlew :desktopApp:packageMsi            # installer; needs the WiX 3 toolset on PATH
```

Without `KGS_DESKTOP_DATA_DIR` the app uses `%LOCALAPPDATA%\KGS Calendar`. Closing the window
keeps it in the notification area so reminders still appear; quit it from the tray icon.
Windows at least 760 dp wide get the desktop layout (`ui/desktop`): a sidebar with the month and
the calendars, a toolbar with back/today/forward, search, views, create and tasks, a task pane
that can be pinned beside the calendar, and a desktop-only year view that scrolls through the
years and lays out each month like a row of the month view. The seams between sidebar, calendar,
task pane and the settings' two columns can be dragged; the sizes are kept in `layout.properties`
in the data folder. Narrower windows use the phone layout.

Search opens over the calendar's column, growing out of the toolbar's search field. The desktop
bundles Roboto (`desktopApp/src/jvmMain/resources/fonts`, SIL OFL), the Android app's font.

Keyboard: Ctrl+N new event, Ctrl+Shift+N new task, Ctrl+F search, T today, Page Up/Down previous
and next range, ←/→ a day in the multi-day view (a range elsewhere), ↑/↓ a row in the month and
year views, D/W/M/Y/A day, multi-day, month, year and agenda, +/− days shown, F5 refresh,
Escape back. All but the arrows and Escape can be changed under Settings → Keyboard shortcuts
(kept in `shortcuts.properties`).

On Windows, a few host tests fail on `main` as well: the DataStore tests that open a second
store on the same file (Windows can't rename over an open file) and a DNS-timing outage test.

## iOS port (in progress)

The iOS port is **not a functional calendar app yet**. `iosApp/KgsCalendar.xcodeproj` is a
component validation host using the permanent KGS Calendar app name and bundle ID.
It exercises the existing Android theme palettes, morphing dropdown, dialogs and create-menu
animations from shared source. It does not create or persist calendar items.

- `:core:shared`: portable settings enums, domain values, reminder offsets and window sizing rules.
- `:shared:ui`: original visual controls, motion tokens, surface colors, timeline pinch/viewport
  calculations, header presentation, fold layout rules and reorder scrolling. Android consumes
  these modules too, so these implementations are not separate iOS copies.
- `:core:data` and `:shared:app` now also build for the desktop JVM (see the Windows section); their
  common code still uses the JDK (`java.time`, OkHttp, ical4j), so it is not yet available to iOS.

The existing gesture/layout characterization tests have moved into `commonTest`, making them
available to JVM and Apple test targets. With JDK 17 and Android SDK 36 configured, run
`bash tools/verify-port.sh` for shared tests, common metadata compilation, the Android debug
build and Android unit tests. CI selects the relevant groups from this same command, including
the relocated shared tests. It uses one Gradle worker, two JVM processors and bounded JVM memory.
The JVM target is for host verification; it does not establish iOS rendering or behavior parity. The initial
Linux verification passed all 54 shared tests and common-code metadata compilation. The Android
APK built successfully, and the Android suites passed 901 tests with 20 live Nextcloud tests
skipped. One date-dependent widget test was corrected to request a size that displays labels even
in six-row months; the production widget renderer was unchanged. The debug APK was installed on
the existing Android emulator, but launch validation was blocked because another device session
held the emulator. No Android visual comparison or Apple runtime validation has been completed.

### Building the validation host on macOS

Requires an Apple Silicon Mac, Xcode 26.0.x with an iOS Simulator runtime, JDK 17 and Android SDK 36
(the root build still includes the Android application). Set `JAVA_HOME` and `ANDROID_HOME`,
or configure the SDK in local, untracked `local.properties`. Open
`iosApp/KgsCalendar.xcodeproj`, select the `KgsCalendar` scheme and an iPhone simulator, and
build/run. The Xcode build phase compiles the Kotlin framework automatically through
`tools/build-apple-framework.sh`, which locates JDK 17 when `JAVA_HOME` is unset and limits
the build to one worker, two JVM processors and a 3 GiB heap for Native release optimization. Simulator
builds need no development team; physical devices need your own signing configuration.

Shared tests on an Apple Silicon simulator: `bash gradlew :core:shared:iosSimulatorArm64Test
:shared:ui:iosSimulatorArm64Test --max-workers=1` (one command). Intel simulator targets are
not configured. The `iOS device archive` job in the `CI` workflow builds an unsigned Release
archive for physical iPhone/iPad devices on an Apple Silicon macOS runner using Xcode 26.0.1.
It validates the packaged icon, privacy manifest, device support and app identity. It is a build
and packaging gate, not a visual or interaction test. Check that workflow before merging;
local Apple compilation and runtime validation remain unavailable on this Linux machine.
Linux cannot run Apple's iOS Simulator; installing another Android emulator does not solve this.

### TestFlight development builds

The [TestFlight workflow](.github/workflows/testflight.yml) uploads the **KGS Calendar**
component host for internal testing on iPhone and iPad. It does not deliver the full calendar
features listed below. The app record and bundle ID `com.kgs501.kgscalendar` are also used for
the eventual App Store release. Uploads use standard App Store Connect distribution, so a
tested release candidate can later be submitted to App Review. This workflow only distributes
to the internal tester group; it does not submit for review or publish an App Store release.
Builds are manually triggered from `main` via **Actions → TestFlight →
Run workflow**; PRs and ordinary pushes do not trigger signing or uploads. The workflow assigns
build numbers from its run number and attempt, verifies API and uploader access before compiling, exports
the signed IPA with Xcode and uploads it with Apple's `altool` on macOS, then checks Apple's processing
and internal tester availability on Linux. If Apple processing times out, rerun only the failed
availability job to avoid rebuilding or uploading again.

One-time account setup requires an Apple Developer membership and an App Store Connect **team**
API key with Admin access, including its Key ID, Issuer ID and downloaded `.p8` file. Keep the key
and signing files outside the repository. With Python 3.12+ and
`python3 -m pip install -r tools/ios/requirements.txt`, set `ASC_KEY_PATH`, `ASC_KEY_ID` and
`ASC_ISSUER_ID`, then:

1. Run `python3 tools/ios/app_store.py register-bundle` to register
   `com.kgs501.kgscalendar`.
2. Create an iOS app record in [App Store Connect](https://appstoreconnect.apple.com/apps) using
   that bundle ID, the name **KGS Calendar**, English (U.S.), and SKU
   `kgs-calendar-ios`. Apple's public API cannot create the initial app record.
3. Run `python3 tools/ios/app_store.py prepare-signing --directory /private/path/kgs-signing`.
   This creates one distribution certificate and App Store provisioning profile and reuses
   that identity on subsequent runs. It does not revoke existing certificates. The directory
   contains the private key, encrypted `.p12`, password, profile and non-secret signing metadata.
4. Run `python3 tools/ios/app_store.py configure-testflight --email your-apple-account-email`.
   The email must belong to an eligible App Store Connect user with access to this app. If
   omitted, the unique account holder is selected. This creates/reuses the internal
   **KGS iOS Development** group and adds that user as a tester.
5. Create a GitHub environment named `testflight`, restrict deployments to `main`, and configure:

   | Environment variables | Value |
   | --- | --- |
   | `ASC_KEY_ID`, `ASC_ISSUER_ID` | From the App Store Connect team key page |
   | `APPLE_TEAM_ID` | `team_id` from the signing directory's `signing.json` |
   | `ASC_APP_ID`, `TESTFLIGHT_GROUP_ID` | `app_id`, `group_id` from tester setup |

   | Environment secrets | Value |
   | --- | --- |
   | `ASC_PRIVATE_KEY` | Contents of the `.p8` file |
   | `APPLE_DISTRIBUTION_P12_BASE64` | Base64-encoded `distribution.p12` |
   | `APPLE_P12_PASSWORD` | Contents of `p12-password` |
   | `APPLE_PROFILE_BASE64` | Base64-encoded `profile.mobileprovision` |

Pass secret values via GitHub's encrypted secret UI or CLI standard input, not command-line
arguments, commits or logs. Signing uses a temporary macOS keychain and removes signing files
after the job. Only failure logs are retained as artifacts. Renew expired certificates/profiles
before future uploads and update the environment secrets together.

After successful delivery, accept Apple's invitation on the iPad and install the build in
[TestFlight](https://testflight.apple.com/). Internal builds are available for 90 days and do not
require an external beta review. Tooling tests can run without Apple credentials or Xcode:
`python3 -m unittest discover -s tools/ios -p 'test_*.py' -v`.

### Remaining work before an iOS release

1. Port the full calendar screens, editors and application state. Replace Android text/canvas,
   locale/resource, haptic, keyboard and window integrations while keeping gesture semantics.
2. Migrate Java date/time usage, `ical4j` parsing, recurrence/time-zone handling and ICS
   round-trip preservation. Reuse the current calendar correctness fixtures as acceptance tests.
3. Migrate Room setup, settings, network/XML operations and files; implement Keychain credential
   storage and preserve CalDAV conflict, offline mutation, deletion and recovery behavior.
4. Implement EventKit device calendars, local notifications, background refresh, incoming ICS
   files and WidgetKit widgets. iOS background execution and widget behavior require platform
   adaptations; Android WorkManager/AlarmManager/RemoteViews do not carry over.
5. Build and run on a Mac, compare the actual calendar interactions against Android, test both
   orientations, safe areas, keyboard, accessibility/font scale and real devices, then complete
   signing and distribution setup.

## Continuous integration

The [CI workflow](.github/workflows/ci.yml) runs on pull requests and pushes to `main`.
Feature-branch pushes do not run a second copy alongside PR checks. A small Linux job tests
the selection logic and checks the complete Git diff before allocating build runners:

| Changed files | Build checks |
| --- | --- |
| Android app, resources, data layer or Android-specific shared source | Android APK and Android unit tests on Linux |
| Swift/Xcode host, TestFlight tooling, or Apple-specific shared source | iOS Release device archive and packaging validation on macOS |
| Shared production code | Android, shared JVM tests/common metadata, and iOS |
| Shared JVM source or common/JVM tests only | Shared JVM tests and common metadata on Linux |
| Gradle configuration (including Android module build scripts), CI configuration, or unknown paths | All checks |
| Documentation, root README/license/privacy pages, logos, or known standalone development tools | Selection tests only; platform builds skipped |

Mixed changes run the union of their checks. Renames include both old and new paths, pushes
include every changed commit, and an unavailable diff runs all builds. The selector lives in
[`tools/ci/select_builds.py`](tools/ci/select_builds.py); update its tests when adding modules or
making a previously standalone file a build input. Platform skips still produce a successful
`CI result` check; this is the check to require in branch protection. A failed selection or
selected build fails that check.

Gradle caching remains enabled, and macOS also caches the Kotlin/Native toolchain. Main-branch
builds verify integration and refresh caches used by PRs. Superseded runs are cancelled.
Use **Actions → CI → Run workflow** to force `all`, `android`, `shared`, or `ios` checks.
Locally, `bash tools/verify-port.sh` still runs all Linux checks; `--android-only` and
`--shared-only` select a group. Test CI selection without an SDK using
`python3 -m unittest discover -s tools/ci -p 'test_*.py' -v`.

## Privacy

The public privacy policy is available in [index.html](index.html). It describes local device storage, Android calendar access, CalDAV/Nextcloud sync, read-only subscriptions, optional OpenStreetMap location features, encrypted credential storage, backup exclusions, deletion behavior, and contact information.

## License

KGS Calendar is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Copyright (c) 2026 KGS501.

This is a source-available, non-commercial license, not an open-source license. It permits non-commercial use, modification, and distribution, subject to its terms. Commercial use requires explicit written permission from KGS501.

Third-party dependencies, libraries, services, and assets retain their own licenses and attribution requirements.
