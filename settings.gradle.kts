pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            name = "Su5eD"
            url = uri("https://maven.su5ed.dev/releases")
        }
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

rootProject.name = "connector"
