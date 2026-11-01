plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
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
    iosSimulatorArm64()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
