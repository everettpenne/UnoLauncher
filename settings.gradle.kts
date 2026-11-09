pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "DuoLauncher"
include(":app")
// Uno's fork of the Backdrop library: see third_party/backdrop/README.md.
include(":backdrop")
project(":backdrop").projectDir = file("third_party/backdrop")

// Optional isolated experiment; excluded from ordinary launcher builds.
if (providers.gradleProperty("duoProbe").isPresent) include(":discover-probe")
