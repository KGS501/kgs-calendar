plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kgs.calendar.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testFixtures {
        enable = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // This check only concerns lint rule authors, and it crashes the analysis of this
        // module (KtDeclarationModifierList "Bad parent"), which also blocks :app:lintDebug.
        disable += "UElementAsPsi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}


// The database now lives in :core:shared (Room for Android and iOS); its schema history stays here
// for the migration tests, which read it as androidTest assets.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    api(project(":core:shared"))
    // Room entities/DAOs, Flow and OkHttpClient appear in the public API used by :app.
    api(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.android)
    api(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.ical4j)
    // Transitively pinned to 1.7.3 by AndroidX; Room's migration test helper needs >= 1.8.1 at runtime.
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.turbine)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    testFixturesImplementation(libs.androidx.test.core)
    testFixturesImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
