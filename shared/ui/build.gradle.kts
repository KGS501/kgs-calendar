plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.kgs.calendar.shared.ui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get())) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "KgsSharedUi"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        commonMain.dependencies {
            api(project(":core:shared"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.materialIconsExtended)
            api(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
    }
}
