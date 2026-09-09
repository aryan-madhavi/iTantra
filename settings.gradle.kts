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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AstraMesh"

// Core and shared libraries
include(":common")
include(":crypto")
include(":core")
include(":domain")
include(":routing")
include(":ble")
include(":mesh")
include(":storage")
include(":data")
include(":services")
include(":workers")
include(":ui")
include(":feature-chat")
include(":feature-nearby")
include(":feature-settings")
include(":testing")
include(":app")
