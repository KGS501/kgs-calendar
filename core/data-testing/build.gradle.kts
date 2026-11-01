plugins {
    alias(libs.plugins.android.library)
}

// Test doubles and the repository harness, shared by the data and app test suites.
android {
    namespace = "com.kgs.calendar.core.data.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.test.core)
    implementation(libs.mockwebserver)
}
