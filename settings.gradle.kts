pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS keeps all repository declarations centralized here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UnoP2P"

// The pure-Kotlin engine builds and tests with only Maven Central and is
// therefore always part of the build (it is what CI/this sandbox verifies).
include(":engine")

// The Android app module depends on the Android Gradle Plugin + AndroidX,
// which are served only from Google's Maven (google()). Configuration-on-demand
// (see gradle.properties) means running `:engine:test` never configures `:app`,
// so the engine can be built in environments where Google Maven is unreachable.
include(":app")
