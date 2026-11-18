plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.kgs.calendar.core.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get())) }
    }
    jvm()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        // Android and the desktop JVM share ical4j, OkHttp's URL rules and java.time formatting.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ical4j)
                implementation(libs.okhttp)
            }
        }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            // Windows' data protection (DPAPI) for the stored passwords.
            implementation(libs.jna.platform)
        }
        iosMain.dependencies { implementation(libs.androidx.sqlite.bundled) }
    }
}

room {
    // The schema history the Android migrations are tested against stays in :core:data.
    schemaDirectory("$projectDir/../data/schemas")
}

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosX64", "kspIosArm64", "kspIosSimulatorArm64").forEach { configuration ->
        add(configuration, libs.androidx.room.compiler)
    }
}
