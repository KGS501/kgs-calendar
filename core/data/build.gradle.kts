plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.kgs.calendar.core.data"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get())) }

        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        lint {
            // This check only concerns lint rule authors, and it crashes the analysis of this
            // module (KtDeclarationModifierList "Bad parent"), which also blocks :app:lintDebug.
            disable += "UElementAsPsi"
        }
    }
    // The desktop app. The data layer is shared with Android only, so its common code may use the JDK.
    jvm()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        commonMain.dependencies {
            api(project(":core:shared"))
            // Room entities/DAOs, Flow and OkHttpClient appear in the public API used by the apps.
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.okhttp)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.ical4j)
            // Transitively pinned to 1.7.3 by AndroidX; Room's migration test helper needs >= 1.8.1 at runtime.
            implementation(libs.kotlinx.serialization.core)
            // Part of Android itself; the desktop gets the same library (see jvmMain).
            compileOnly(libs.json)
        }
        androidMain.dependencies {
            api(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.security.crypto)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.jna.platform)
            api(libs.json)
        }
        getByName("androidHostTest").dependencies {
            implementation(project(":core:data-testing"))
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.room.testing)
            implementation(libs.mockwebserver)
            implementation(libs.turbine)
            implementation(libs.json)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.room.testing)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}
