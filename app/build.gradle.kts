@file:Suppress("DEPRECATION")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the local publishing environment or keystore.properties at the repo
// root (gitignored). When neither is present, the release build simply stays unsigned instead of
// failing to configure.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val environmentReleaseSigning = mapOf(
    "storeFile" to System.getenv("KGS_RELEASE_STORE_FILE"),
    "storePassword" to System.getenv("KGS_RELEASE_STORE_PASSWORD"),
    "keyAlias" to System.getenv("KGS_RELEASE_KEY_ALIAS"),
    "keyPassword" to System.getenv("KGS_RELEASE_KEY_PASSWORD"),
)
val hasEnvironmentReleaseSigning = environmentReleaseSigning.values.all { !it.isNullOrBlank() }
val hasReleaseSigning = keystorePropertiesFile.exists() || hasEnvironmentReleaseSigning

fun releaseSigningValue(propertyName: String): String =
    environmentReleaseSigning[propertyName]
        ?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)
        ?: error("Missing release signing value: $propertyName")

android {
    namespace = "com.kgs.calendar"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kgs501.kgscalendar"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 36
        versionCode = 27
        versionName = "V.1.3.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningValue("storeFile"))
                storePassword = releaseSigningValue("storePassword")
                keyAlias = releaseSigningValue("keyAlias")
                keyPassword = releaseSigningValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric widget rendering tests resolve app strings and layouts.
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/groovy/**",
                "zoneinfo-global/**",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.turbine)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
