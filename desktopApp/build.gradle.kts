import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/** The Android version name without its prefix, e.g. "V.1.4.6-3" → "1.4.6-3". */
val appVersionName = "V.1.4.6-3"

kotlin {
    jvm()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared:app"))
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kgs.calendar.desktop.MainKt"
        jvmArgs += listOf("-Dkgs.version=$appVersionName", "-Xss2m")
        // Development runs: `KGS_DESKTOP_DATA_DIR=<folder> KGS_DESKTOP_DEMO=1 gradlew :desktopApp:run`.
        System.getenv("KGS_DESKTOP_DATA_DIR")?.let { args += listOf("--data-dir", it) }
        if (System.getenv("KGS_DESKTOP_DEMO") == "1") args += "--demo"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "KGS Calendar"
            // Windows installers need a plain MAJOR.MINOR.BUILD version.
            packageVersion = appVersionName.removePrefix("V.").substringBefore('-')
            description = "Calendar and tasks for CalDAV and Nextcloud"
            vendor = "KGS501"
            copyright = "KGS501"
            includeAllModules = true
            windows {
                iconFile.set(project.file("icons/kgs_calendar.ico"))
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Keeps updates installing over the previous version; never change it.
                upgradeUuid = "e37ae039-a4f6-46a7-a7fd-14f524e68795"
            }
        }
    }
}
