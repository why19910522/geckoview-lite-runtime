pluginManagement {
    repositories {
        providers.gradleProperty("localBuildRepository").orNull?.let { local ->
            maven { url = uri(local) }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val geckoRepository = providers.gradleProperty("geckoRepository").get()
require(file(geckoRepository).isDirectory) { "geckoRepository must be a local Maven directory" }
val upstreamDependencyRepository = providers.gradleProperty("upstreamDependencyRepository").orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri(geckoRepository) } }
            filter { includeGroup("org.mozilla.geckoview") }
        }
        providers.gradleProperty("localBuildRepository").orNull?.let { local ->
            maven { url = uri(local) }
        }
        google()
        mavenCentral()
        upstreamDependencyRepository?.let { repository ->
            maven {
                url = uri(repository)
                content { includeGroupByRegex("org\\.mozilla(\\..*)?") }
            }
        }
    }
}

rootProject.name = "geckoview-sdk35-smoke"
include(":app")

if (providers.gradleProperty("externalCore").getOrElse("false").toBoolean()) {
    include(":runtime-external")
    project(":runtime-external").projectDir = file("../runtime-external")
}
