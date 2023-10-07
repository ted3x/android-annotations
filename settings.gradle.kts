pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

rootProject.name = "android-annotations"
include("annotations")
include("compiler")
include("integration-test")
