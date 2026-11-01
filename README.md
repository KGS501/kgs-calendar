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

## iOS port (in progress)

The iOS port is **not a functional calendar app yet**. `iosApp/KgsCalendar.xcodeproj` is a
component validation host, with an intentionally separate bundle ID and display name.
It exercises the existing Android theme palettes, morphing dropdown, dialogs and create-menu
animations from shared source. It does not create or persist calendar items.

- `:core:shared`: portable settings enums, domain values, reminder offsets and window sizing rules.
- `:shared:ui`: original visual controls, motion tokens, surface colors, timeline pinch/viewport
  calculations, header presentation, fold layout rules and reorder scrolling. Android consumes
  these modules too, so these implementations are not separate iOS copies.
- `:core:data` and the remaining `:app` code are still Android-specific.

The existing gesture/layout characterization tests have moved into `commonTest`, making them
available to JVM and Apple test targets. With JDK 17 and Android SDK 36 configured, run
`bash tools/verify-port.sh` for shared tests, common metadata compilation, the Android debug
build and Android unit tests. Android CI runs this same command, including the relocated shared
tests. It uses one Gradle worker, two JVM processors and bounded JVM memory. The JVM target
is for host verification; it does not establish iOS rendering or behavior parity. The initial
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
`tools/build-apple-framework.sh`, which locates JDK 17 when `JAVA_HOME` is unset. Simulator
builds need no development team; physical devices need your own signing configuration.

Shared tests on an Apple Silicon simulator: `bash gradlew :core:shared:iosSimulatorArm64Test
:shared:ui:iosSimulatorArm64Test --max-workers=1` (one command). Intel simulator targets are
not configured. The `iOS validation host CI` workflow builds and links the unsigned simulator
app on an Apple Silicon macOS runner using Xcode 26.0.1. It is a compile/link gate, not a visual
or interaction test. Apple compilation results must be checked in that workflow before merging;
local Apple compilation and runtime validation remain unavailable on this Linux machine.
Linux cannot run Apple's iOS Simulator; installing another Android emulator does not solve this.

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

## Privacy

The public privacy policy is available in [index.html](index.html). It describes local device storage, Android calendar access, CalDAV/Nextcloud sync, read-only subscriptions, optional OpenStreetMap location features, encrypted credential storage, backup exclusions, deletion behavior, and contact information.

## License

KGS Calendar is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Copyright (c) 2026 KGS501.

This is a source-available, non-commercial license, not an open-source license. It permits non-commercial use, modification, and distribution, subject to its terms. Commercial use requires explicit written permission from KGS501.

Third-party dependencies, libraries, services, and assets retain their own licenses and attribution requirements.
