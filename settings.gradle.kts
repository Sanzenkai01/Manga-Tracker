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

rootProject.name = "MangaTracker"

include(
    ":app",
    ":core:domain",
    ":core:data",
    ":core:localization",
    ":core:parser-api",
    ":core:parser-engine",
    ":parsers:site-adapters",
)
