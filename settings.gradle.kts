pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
        maven {
            name = "Sponge Snapshots"
            url = uri("https://repo.spongepowered.org/repository/maven-public/")
        }
        maven {
            name = "Su5eD"
            url = uri("https://maven.su5ed.dev/releases")
        }
    }
}

rootProject.name = "Connector"
