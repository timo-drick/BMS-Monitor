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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // Do not change the username below.
                // This should always be `mapbox` (not your username).
                username = "mapbox"
                val mapboxToken = System.getenv("MAPBOX_MAVEN_TOKEN") ?:
                java.util.Properties().let { properties ->
                    properties.load(file("local.properties").bufferedReader())
                    properties.getProperty("MAPBOX_MAVEN_TOKEN")
                }
                // Use the secret token you stored in gradle.properties as the password
                password = mapboxToken
            }
        }
    }
}

rootProject.name = "BMS Monitor"
include(":app")
 