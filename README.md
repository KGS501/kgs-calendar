# KGS Calendar <a href="https://play.google.com/store/apps/details?id=com.kgs501.kgscalendar"><img alt="Get KGS Calendar on Google Play" src="https://img.shields.io/badge/Get_it_on-Google_Play-414141?style=for-the-badge&logo=googleplay&logoColor=white"></a>

<img width="1794" height="876" alt="KGS Calendar screenshot" src="https://github.com/user-attachments/assets/b2c5feb6-cbca-4330-bf6d-fe05e6e2ead8" />

KGS Calendar is a calendar and task app for Android and iOS for managing events, tasks, reminders, and synchronized calendar sources. It can be used offline with local app data, with the device's own calendars (Android device calendars, iPhone calendars), or with CalDAV/Nextcloud calendars and task lists.

If you like the App and want to support me continuing the development, consider sponsoring the project!

<a href="https://github.com/sponsors/KGS501"><img height="56" alt="Sponsor KGS501 on GitHub" src="https://img.shields.io/badge/Sponsor-KGS501-EA4AAA?style=for-the-badge&amp;logo=githubsponsors&amp;logoColor=white"></a>

## Available on Google Play Store

Get automatic updates and don't risk downloading any unstable pre-release versions

<a href="https://play.google.com/store/apps/details?id=com.kgs501.kgscalendar"><img height="56" alt="Get KGS Calendar on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"></a>

## Features

- Events and tasks in one app
- Agenda, day, 3-day, month, and task-list views
- Local calendar support without a server
- Device calendar access when permission is granted (Android device calendars, iPhone calendars)
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
- Home screen widget support (Android)
- English and German app language support

## Windows app

The Windows app (`:desktopApp`) runs the same screens, view models, sync and database as the
Android and iOS apps, built from the same Kotlin code with Compose Multiplatform. `:core:shared`
and `:shared:ui` have a desktop (JVM) target for it; their `jvmMain` folders hold the Windows
parts:

- Passwords are encrypted with Windows' data protection (DPAPI) for the signed-in user.
- Reminders appear while the app runs. Closing the window keeps it in the notification area
  (Settings → General can turn that off); quit it from the tray icon's menu.
- Settings → General can open the app when Windows starts. That is Windows' own startup entry for
  the user, so it also appears in Windows' Settings → Apps → Startup and follows changes made there.
- The data lives in `%LOCALAPPDATA%\KGS501\KGS Calendar`, apart from the program folder, so
  installing an update keeps it. Previews before V.2.0.0 kept it in the program folder.
- The official way to get it is the Microsoft Store, built with `packageMsix`; see
  [docs/microsoft-store/README.md](docs/microsoft-store/README.md). The `.msi` stays available on
  GitHub. A Store app can't write to the registry's startup list or its own folder, so it uses a
  startup task, keeps its data in its package, and doesn't build a class archive.
- Running the installer while the app is open closes it and starts the new version afterwards.
  Windows only installs over a lower version, so every build handed out needs a higher
  `appVersionName` in `desktopApp/build.gradle.kts` (V.2.0.0 is installer version 2.0.0, V.2.0.0-1 is 2.0.1, V.2.0.1 is 2.0.100).

Windows at least 760 dp wide get the desktop layout (`ui/desktop`): a sidebar with the month and
the calendars, a toolbar with back/today/forward, search, views, create and tasks, a task pane
that can be pinned beside the calendar, and a desktop-only year view. The seams between sidebar,
calendar, task pane and the settings' two columns can be dragged (kept in `layout.properties`).
Cards, tasks' circles and calendars have right-click menus. Narrower windows use the phone layout.

Keyboard: Ctrl+N new event, Ctrl+Shift+N new task, Ctrl+F search, T today, Page Up/Down previous
and next range, ←/→ a day in the multi-day view (a range elsewhere), ↑/↓ a row in the month and
year views, D/W/M/Y/A day, multi-day, month, year and agenda, +/− days shown, F5 refresh,
Escape back. All but the arrows and Escape can be changed under Settings → Keyboard shortcuts.

Run it with JDK 17 from the repository root (the Android SDK is still needed to configure the
build). Use a separate profile and synthetic items for testing:

```
KGS_DESKTOP_DATA_DIR=<empty folder> KGS_DESKTOP_DEMO=1 ./gradlew :desktopApp:run
./gradlew :desktopApp:createDistributable   # portable app in desktopApp/build/compose/binaries
./gradlew :desktopApp:packageMsi            # installer; needs the WiX 3 toolset on PATH
```

## iOS app

From V.2.0.0 the iOS app is the Android app's port: the same screens, gestures, animations, sync,
editors and settings, built from the same Kotlin code with Compose Multiplatform. It uses the
system font (SF) instead of Roboto.

- `:core:shared` (Kotlin Multiplatform) holds the data layer: Room database, CalDAV/Nextcloud and
  ICS sync, iCalendar parsing and writing, recurrence expansion, settings and reminders planning.
  On Android it uses ical4j and OkHttp; on iOS its own iCalendar/RRULE code (checked against ical4j
  in `CommonIcalVsIcal4jTest`) and NSURLSession.
- `:shared:ui` holds the whole UI and its view models; `androidMain`/`iosMain` provide the platform
  parts (permissions, pickers, maps, haptics, window and fold layout, file export).
- `:core:data` and `:app` keep what only Android has: the calendar provider, encrypted
  preferences, widgets, WorkManager and AlarmManager.

iOS specifics:

- Passwords are stored in the iOS Keychain for this device only.
- Reminders are local notifications with Snooze and, for tasks, Mark done. iOS keeps at most 64
  pending notifications, so the earliest reminders of the next three weeks are scheduled and
  re-planned after every sync, edit and app start.
- Changes upload right away; while the app is open it refreshes every minute, in the background
  through `BGTaskScheduler` (`com.kgs501.kgscalendar.refresh`) when iOS allows it.
- iPhone calendars (iCloud, Google, Exchange accounts of the Calendar app) come from EventKit.
- `.ics` files opened with the app (Files, Mail, the share sheet) can be imported.
- The `KgsShare` share extension creates an event from shared text, like Android's share target;
  it opens the app through the `kgscalendar://share` URL.
- Home-screen widgets are not available on iOS yet.

### Building the iOS app on macOS

Requires an Apple Silicon Mac, Xcode 26.0.x with an iOS Simulator runtime, JDK 17 and Android SDK 36
(the root build still includes the Android application). Set `JAVA_HOME` and `ANDROID_HOME`,
or configure the SDK in local, untracked `local.properties`. Open
`iosApp/KgsCalendar.xcodeproj`, select the `KgsCalendar` scheme and an iPhone simulator, and
build/run. The Xcode build phase compiles the Kotlin framework automatically through
`tools/build-apple-framework.sh`, which locates JDK 17 when `JAVA_HOME` is unset and limits
the build to one worker, two JVM processors and a 5 GiB heap (`KGS_APPLE_FRAMEWORK_HEAP` overrides it) for Native release optimization. Simulator
builds need no development team; physical devices need your own signing configuration.

Shared tests on an Apple Silicon simulator: `bash gradlew :core:shared:iosSimulatorArm64Test
:shared:ui:iosSimulatorArm64Test --max-workers=1` (one command). Intel Macs use the `iosX64`
target: build with `ARCHS=x86_64` for an Intel simulator. The `iOS device archive` job in the `CI` workflow builds an unsigned Release
archive for physical iPhone/iPad devices on an Apple Silicon macOS runner using Xcode 26.0.1.
It validates the packaged icon, privacy manifest, device support and app identity. It is a build
and packaging gate, not a visual or interaction test. Check that workflow before merging;
local Apple compilation and runtime validation remain unavailable on this Linux machine.
Linux cannot run Apple's iOS Simulator; installing another Android emulator does not solve this.

### TestFlight development builds

The [TestFlight workflow](.github/workflows/testflight.yml) uploads the **KGS Calendar**
iOS app for internal testing on iPhone and iPad. The app record and bundle IDs `com.kgs501.kgscalendar` and `com.kgs501.kgscalendar.share` (share extension) are also used for
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
   `com.kgs501.kgscalendar` and the share extension's `com.kgs501.kgscalendar.share`.
2. Create an iOS app record in [App Store Connect](https://appstoreconnect.apple.com/apps) using
   that bundle ID, the name **KGS Calendar**, English (U.S.), and SKU
   `kgs-calendar-ios`. Apple's public API cannot create the initial app record.
3. Run `python3 tools/ios/app_store.py prepare-signing --directory /private/path/kgs-signing`.
   This creates one distribution certificate and App Store provisioning profiles for the app and
   its share extension (`profile.mobileprovision`, `share-profile.mobileprovision`), and reuses
   that identity on subsequent runs; rerunning it on an existing setup only adds what is missing. It does not revoke existing certificates. The directory
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
   | `APPLE_SHARE_PROFILE_BASE64` | Optional: base64-encoded `share-profile.mobileprovision`. Without it the upload creates or reuses the share extension's profile through the API key. |

Pass secret values via GitHub's encrypted secret UI or CLI standard input, not command-line
arguments, commits or logs. Signing uses a temporary macOS keychain and removes signing files
after the job. Only failure logs are retained as artifacts. Renew expired certificates/profiles
before future uploads and update the environment secrets together.

After successful delivery, accept Apple's invitation on the iPad and install the build in
[TestFlight](https://testflight.apple.com/). Internal builds are available for 90 days and do not
require an external beta review. Tooling tests can run without Apple credentials or Xcode:
`python3 -m unittest discover -s tools/ios -p 'test_*.py' -v`.

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

The public privacy policy is available in [index.html](index.html). It covers the Android, iOS and Windows apps: local device storage, device calendar access, local notifications, CalDAV/Nextcloud sync, read-only subscriptions, sharing and imports, optional OpenStreetMap location features, credential storage, backups, deletion behavior, and contact information.

## License

KGS Calendar is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Copyright (c) 2026 KGS501.

This is a source-available, non-commercial license, not an open-source license. It permits non-commercial use, modification, and distribution, subject to its terms. Commercial use requires explicit written permission from KGS501.

Third-party dependencies, libraries, services, and assets retain their own licenses and attribution requirements.
