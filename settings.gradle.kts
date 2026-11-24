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
        // Wajib untuk NewPipeExtractor.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Dengerin"
include(":app")
