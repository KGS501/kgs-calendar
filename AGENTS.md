# AGENTS.md

## Project

- Single-module Android app for KGS Calendar. Work from the repository root.
- Use the checked-in Gradle wrapper: `.\gradlew.bat`, not a system Gradle install.
- Android config: namespace `com.kgs.calendar`, app id `com.kgs501.kgscalendar`, debug suffix `.debug`, min SDK 26, compile/target SDK 36.
- Main entry points: `KgsCalendarApplication.kt`, `MainActivity.kt`, `AppGraph.kt`.
- Main UI is Jetpack Compose/Material 3 under `app/src/main/java/com/kgs/calendar/ui`.
- Widgets are classic `RemoteViews` under `app/src/main/java/com/kgs/calendar/widget` plus `res/layout` and `res/drawable`; they are not Compose.
- Persistence uses Room under `data/local`; settings use DataStore under `data/settings`; sync uses WorkManager under `sync`.
- Localized strings live in `res/values/strings.xml` and `res/values-de/strings.xml`.

## Build Environment

- Gradle/Kotlin must run on JDK 17. Check with `.\gradlew.bat --version`; `Launcher JVM` should be version 17.
- If Java is not 17, set `JAVA_HOME` to a JDK 17 install in the same shell before running Gradle.
- `local.properties` is local-only and should point `sdk.dir` at an Android SDK. Do not commit machine-specific SDK changes.
- Release signing uses root `keystore.properties` when present. Do not print, edit, or depend on release signing for debug work.
- Run Gradle tasks sequentially in this workspace. Parallel `assemble`/`test` invocations can contend for Gradle/Kotlin daemons and hang.
- Cold Kotlin/Compose compilation can be slow, especially around the large `KgsCalendarApp.kt` file.
- Existing project-wide Kotlin/deprecation warnings are noisy; do not treat warning output alone as a failed build.

## Common Commands

- Build debug APK: `.\gradlew.bat :app:assembleDebug`
- Run unit tests: `.\gradlew.bat :app:testDebugUnitTest`
- Run connected tests: `.\gradlew.bat :app:connectedDebugAndroidTest`
- Check connected devices: `.\ADB\adb.exe devices`
- Install debug APK: `.\ADB\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`

## Testing Notes

- Prefer targeted unit tests for model/layout logic, then run `:app:testDebugUnitTest` before finishing non-trivial changes.
- Widget behavior often needs device or launcher verification after install; unit tests and APK builds do not fully prove home-screen widget rendering, click latency, or resize behavior.
- Connected tests require an attached/emulated device and are not a substitute for launcher widget checks.

## Repo Hygiene

- Keep changes scoped. Do not touch local SDK paths, signing files, generated APKs, heap dumps, `tmp`, or build artifacts unless explicitly asked.
- Be careful with root secrets/artifacts such as `keystore.properties` and `release-keystore.jks`.
