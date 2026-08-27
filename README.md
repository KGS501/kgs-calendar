# KGS Calendar

<img width="1794" height="876" alt="KGS Calendar screenshot" src="https://github.com/user-attachments/assets/b2c5feb6-cbca-4330-bf6d-fe05e6e2ead8" />

KGS Calendar is an Android calendar and task app for managing events, tasks, reminders, and synchronized calendar sources. It can be used offline with local app data, with Android device calendars, or with CalDAV/Nextcloud calendars and task lists.

## Features

- Events and tasks in one app
- Agenda, day, 3-day, month, and task-list views
- Local calendar support without a server
- Android device calendar access when permission is granted
- CalDAV and Nextcloud calendar and task sync
- Read-only ICS and CalDAV calendar subscriptions
- Reminders for events and tasks
- Recurring events and tasks
- Search across events and tasks
- Calendar colors, ordering, and visibility controls
- Optional location search through OpenStreetMap Nominatim
- Optional map previews using OpenStreetMap tiles
- Light and dark themes
- Home screen widget support
- English and German app language support

## Build And Test

This repository contains a single-module Android app. Work from the repository root and use the checked-in Gradle wrapper rather than a system Gradle install.

The Android application uses namespace `com.kgs.calendar`, application id `com.kgs501.kgscalendar`, min SDK 26, and compile/target SDK 36. Gradle and Kotlin must run on JDK 17.

Check the Gradle runtime:

```powershell
.\gradlew.bat --version
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Run debug unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Publish To Google Play Internal Testing

Publishing runs locally from this machine; it does not use GitHub Actions. Complete the one-time
credential and upload-key setup with the interactive wizard:

```bash
bash tools/setup_google_play_publishing.sh
```

The wizard stores the service-account key, upload keystore, and signing configuration under
`~/.config/kgs-calendar/` with owner-only permissions. Nothing secret is written to this repository.
If the original upload key is unavailable, the wizard can generate a replacement certificate and
walk through requesting an upload-key reset in Play Console.

Verify the signed build and Google Play access without uploading anything:

```bash
python3 tools/play_publisher.py verify
```

Build, test, and publish a completed release to the `internal` track:

```bash
python3 tools/play_publisher.py publish --release-notes "Your release notes" --yes
```

To publish an already-built signed bundle instead, pass `--aab /path/to/app-release.aab`. Each real
release requires an explicit `--yes`, and publishing refuses to cancel changes already under review.

Connected Android tests and widget rendering checks require an attached device or emulator:

```powershell
.\ADB\adb.exe devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Privacy

The public privacy policy is available in [index.html](index.html). It describes local device storage, Android calendar access, CalDAV/Nextcloud sync, read-only subscriptions, optional OpenStreetMap location features, encrypted credential storage, backup exclusions, deletion behavior, and contact information.

## License

KGS Calendar is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Copyright (c) 2026 KGS501.

This is a source-available, non-commercial license, not an open-source license. It permits non-commercial use, modification, and distribution, subject to its terms. Commercial use requires explicit written permission from KGS501.

Third-party dependencies, libraries, services, and assets retain their own licenses and attribution requirements.
