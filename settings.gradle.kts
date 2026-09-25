pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // CameraX is mirrored locally for deterministic builds in restricted/offline environments.
        maven { url = uri(rootDir.resolve("../offline-m2")) }
        google()
        mavenCentral()
    }
}

rootProject.name = "SHELTER"
include(":app")
