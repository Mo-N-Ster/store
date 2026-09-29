pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "StoreAndroid"
include(":app", ":presentation", ":application-api", ":application", ":domain", ":infrastructure", ":testing")
