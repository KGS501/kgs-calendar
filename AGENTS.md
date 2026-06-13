# AGENTS.md

## Working Here

This is a single-module Android project for KGS Calendar. Work from the repository root and use the checked-in Gradle wrapper instead of a system Gradle install.

Required runtime:

- Use JDK 17 for Gradle and Kotlin. Verify with `.\gradlew.bat --version`; the launcher JVM should report version 17.
- If Gradle sees the wrong Java, set `JAVA_HOME` to a JDK 17 install in the same shell before building.
- `local.properties` is local-only and should point `sdk.dir` at an Android SDK. Do not commit machine-specific SDK changes.
- Release signing is read from root `keystore.properties` when present. Do not print, edit, or depend on it for debug work.

Common commands:

- Build debug APK: `.\gradlew.bat :app:assembleDebug`
- Run unit tests: `.\gradlew.bat :app:testDebugUnitTest`
- Run connected tests: `.\gradlew.bat :app:connectedDebugAndroidTest`
- Check connected devices: `.\ADB\adb.exe devices`
- Install debug build: `.\ADB\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`

## Project Shape

- Gradle config: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.
- Android config: namespace `com.kgs.calendar`, app id `com.kgs501.kgscalendar`, debug suffix `.debug`, min SDK 26, compile/target SDK 36.
- Entry points: `KgsCalendarApplication.kt`, `MainActivity.kt`, and `AppGraph.kt`.
- Main UI is Jetpack Compose/Material 3, mostly under `app/src/main/java/com/kgs/calendar/ui`.
- Persistence uses Room under `data/local`; settings use DataStore under `data/settings`; sync uses WorkManager under `sync`.
- Strings are localized in `res/values/strings.xml` and `res/values-de/strings.xml`.

# 
