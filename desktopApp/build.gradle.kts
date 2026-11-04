import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/** The app's version as the other platforms show it: V.MAJOR.MINOR.PATCH, or V.MAJOR.MINOR.PATCH-BUILD for a rebuild. */
val appVersionName = "V.2.0.0-1"

/**
 * The installer's version. Windows only installs a package over the one before it (keeping its data)
 * when this version is higher, and it compares just three numbers, so every build handed out needs
 * a higher one: V.2.0.0 → 2.0.0, V.2.0.0-3 → 2.0.3, V.2.0.1 → 2.0.100.
 */
val installerVersion: String = run {
    val match = Regex("""V\.(\d+)\.(\d+)\.(\d+)(?:-(\d+))?""").matchEntire(appVersionName)
        ?: error("Unexpected version name $appVersionName")
    val (major, minor, patch, build) = match.destructured
    val buildNumber = patch.toInt() * 100 + (build.toIntOrNull() ?: 0)
    check(build.isEmpty() || build.toInt() < 100) { "A rebuild number must stay below 100" }
    "$major.$minor.$buildNumber"
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared:ui"))
            implementation(compose.desktop.currentOs)
            implementation(libs.jna.platform)
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
            // The JVM options file (see below), in the app's resources folder.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "KGS Calendar"
            // Windows installers need a plain MAJOR.MINOR.BUILD version.
            packageVersion = installerVersion
            // Windows shows this as the program's name (Task Manager, "Open with").
            description = "KGS Calendar"
            vendor = "KGS501"
            copyright = "KGS501"
            // From suggestRuntimeModules, plus what is only used at runtime: TLS key exchange for
            // servers, localized month and day names, logging, JNDI for ical4j and screen readers;
            // jdk.management writes the class archive (ClassDataArchive).
            modules(
                "java.instrument", "java.sql", "jdk.unsupported", "java.logging", "java.naming",
                "java.management", "jdk.management", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets",
                "jdk.accessibility",
            )
            windows {
                iconFile.set(project.file("icons/kgs_calendar.ico"))
                menu = true
                // Straight into the Start menu's list, not into a folder (jpackage's default is one called "Unknown").
                menuGroup = ""
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Keeps updates installing over the previous version; never change it (UpdateWatcher knows it too).
                upgradeUuid = "e37ae039-a4f6-46a7-a7fd-14f524e68795"
            }
        }
    }
}

// Faster starts: the JVM loads classes from class-data-sharing archives instead of reading and verifying
// them from the jars:
// - the runtime's own (JDK) classes from bin/server/classes.jsa, made here once the runtime is linked;
// - the app's classes from an archive the app builds on the user's computer (ClassDataArchive), from
//   the list of classes it loads; kgs-jvm.options then names it. Only the packaged app gets these
//   options: `$APPDIR` exists only there.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    val jdkHome = javaHome
    val runtimeDir = destinationDir
    doLast {
        val runtime = runtimeDir.get().asFile
        // The linked runtime has no launcher. The JDK's own (the same version) runs the runtime's JVM;
        // javaw stays, for the app to build its archive with.
        val java = File(runtime, "bin/java.exe")
        File(jdkHome.get(), "bin/java.exe").copyTo(java, overwrite = true)
        File(jdkHome.get(), "bin/javaw.exe").copyTo(File(runtime, "bin/javaw.exe"), overwrite = true)
        try {
            val process = ProcessBuilder(java.absolutePath, "-Xshare:dump").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0 && File(runtime, "bin/server/classes.jsa").isFile) { "CDS dump failed:\n$output" }
        } finally {
            java.delete()
        }
    }
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    // Every package is made from scratch (the installers don't reuse the app image), so each gets them.
    run {
        freeArgs.addAll(
            "--java-options", "-XX:VMOptionsFile=\$APPDIR/resources/kgs-jvm.options",
            "--java-options", "-XX:DumpLoadedClassList=\$APPDIR/resources/kgs-classes.lst",
        )
    }
}
