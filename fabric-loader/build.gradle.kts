import net.minecraftforge.gradle.common.util.RunConfig

plugins {
    `java-library`
    id("net.minecraftforge.gradle")
}

val versionMc: String by rootProject

minecraft {
    mappings("official", versionMc)

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            property("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE")
            workingDirectory = project.file("run").canonicalPath

            mods {
                create("connector") {
                    sources(sourceSets.main.get())
                }
            }
        }

        create("client", config)
        create("server", config)
    }
}

sourceSets {
	main {
		java {
            srcDirs("src/main/java", "src/main/legacyJava")
        }
	}
}

dependencies { 
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-45.0.64")
}

// TODO Api compat check