pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KGS Calendar"
include(":app")
include(":core:data")
include(":core:data-testing")

include(":core:shared", ":shared:ui")
include(":shared:app")
include(":desktopApp")
