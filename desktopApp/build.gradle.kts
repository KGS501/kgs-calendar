import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

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
        System.getenv("KGS_DESKTOP_DATA_DIR")?.let { args += listOf("--data-dir", it) }
        if (System.getenv("KGS_DESKTOP_DEMO") == "1") args += "--demo"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "KGS Calendar"
            packageVersion = "1.4.6"
            vendor = "KGS501"
        }
    }
}
