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

Connected Android tests and widget rendering checks require an attached device or emulator:

```powershell
.\ADB\adb.exe devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Privacy

The public privacy policy is available in [index.html](index.html). It describes local device storage, Android calendar access, CalDAV/Nextcloud sync, read-only subscriptions, optional OpenStreetMap location features, encrypted credential storage, backup exclusions, deletion behavior, and contact information.

## License

This repository is published without an open-source license.

Copyright © 2026 KGS501. All rights reserved.

No permission is granted to copy, modify, distribute, sublicense, or use the source code except with explicit written permission from KGS501 or as otherwise permitted by applicable law.

See [NO-LICENSE.md](NO-LICENSE.md) for the repository notice. Third-party dependencies, libraries, services, and assets retain their own licenses and attribution requirements.
