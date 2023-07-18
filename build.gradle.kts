import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.common.util.RunConfig
import java.time.LocalDateTime

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("org.spongepowered.mixin") version "0.7.+"
}

version = "1.0"
group = "dev.su5ed.sinytra"

val versionMc: String by project
val versionForge: String by project
val versionForgeAutoRenamingTool: String by project
val versionFabricLoader: String by project
val versionAccessWidener: String by project
val versionFabricApi: String by project
val versionMixin: String by project

val language by sourceSets.registering
val mod: SourceSet by sourceSets.creating

val shade: Configuration by configurations.creating
val shadeRuntimeOnly: Configuration by configurations.creating

val depsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(shade, shadeRuntimeOnly)

    exclude("assets/fabricloader/**")
    exclude("META-INF/**")
    exclude("ui/**")
    exclude("*.json")
    exclude("module-info.class")
    exclude("LICENSE.txt")

    dependencies {
        exclude(dependency("org.ow2.asm:"))
        exclude(dependency("net.sf.jopt-simple:"))
        exclude(dependency("com.google.guava:guava"))
        exclude(dependency("com.google.code.gson:gson"))
    }

    archiveClassifier.set("deps")
}
val languageJar: Jar by tasks.creating(Jar::class) {
    dependsOn("languageClasses")

    from(language.get().output)
    manifest.attributes("FMLModType" to "LANGPROVIDER")

    archiveClassifier.set("language")
}
val modJar: Jar by tasks.creating(ShadowJar::class) {
    dependsOn("modClasses")

    from(mod.output)
    relocate("org.spongepowered.asm", "org.spongepowered.reloc.asm")
    manifest.attributes("ConnectorMixinConfigs" to "connectormod.mixins.json")

    archiveClassifier.set("mod")
}
val remappedDepsJar: Jar by tasks.creating(ShadowJar::class) {
    dependsOn(depsJar)

    from(tasks.jar.flatMap { it.archiveFile })
    from(depsJar.archiveFile)
    mergeServiceFiles() // Relocate services
    relocate("org.spongepowered.asm", "org.spongepowered.reloc.asm")
    relocate("org.spongepowered.include", "org.spongepowered.reloc.include")
    relocate("org.spongepowered.tools", "org.spongepowered.reloc.tools")
    relocate("net.minecraftforge.fart", "net.minecraftforge.reloc.fart")
    relocate("net.minecraftforge.srgutils", "net.minecraftforge.reloc.srgutils")
    relocate("MixinConfigs", "ConnectorMixinConfigs")
    archiveClassifier.set("deps-reloc")
}
val fullJar: Jar by tasks.creating(Jar::class) {
    mustRunAfter("reobfModJar")
    from(zipTree(remappedDepsJar.archiveFile))
    from(languageJar)
    from(modJar)
    manifest.attributes("Embedded-Dependencies-Language" to languageJar.archiveFile.get().asFile.name)
    manifest.attributes("Embedded-Dependencies-Mod" to modJar.archiveFile.get().asFile.name)

    archiveClassifier.set("full")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

mixin {
    add(mod, "mixins.connectormod.refmap.json")
}

reobf {
    create("modJar") {
        dependsOn(modJar)
    }
}

configurations {
    compileOnly {
        extendsFrom(shade)
    }

    "languageImplementation" {
        extendsFrom(configurations.minecraft.get(), shade)
    }

    "modCompileOnly" {
        extendsFrom(configurations.compileOnly.get())
    }

    "modImplementation" {
        extendsFrom(configurations.minecraft.get(), shade)
    }

    "modAnnotationProcessor" {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

sourceSets {
    main {
        runtimeClasspath = runtimeClasspath.minus(output).plus(files(fullJar))
    }
}

println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
minecraft {
    mappings("official", versionMc)

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            property("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE,COREMOD")
            property("connector.logging.markers", "MIXINPATCH")
            property("mixin.debug", "true")
//            property("connector.cache.enabled", "false")
            workingDirectory = project.file("run").canonicalPath

            mods {
                create("connector") {
                    sources(sourceSets.main.get())
                }
            }

            val existing = lazyTokens["minecraft_classpath"]
            lazyToken("minecraft_classpath") {
                fullJar.archiveFile.get().asFile.absolutePath
                    .let { path -> existing?.let { "$path;${it.get()}" } ?: path }
            }
        }

        create("client", config)
        create("server", config)

        create("data") {
            config(this)
            args(
                "--mod", "connector",
                "--all",
                "--output", file("src/generated/resources/"),
                "--existing", file("src/main/resources/")
            )
        }
    }
}

// Include resources generated by data generators.
sourceSets.main {
    resources {
        srcDir("src/generated/resources")
    }
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net")
    }
    maven {
        name = "Su5eD"
        url = uri("https://maven.su5ed.dev/releases")
    }
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-$versionForge")

    shade(group = "dev.su5ed.sinytra", name = "fabric-loader", version = versionFabricLoader) { isTransitive = false }
    shade(group = "net.fabricmc", name = "access-widener", version = versionAccessWidener)
    shade(group = "dev.su5ed.sinytra", name = "ForgeAutoRenamingTool", version = versionForgeAutoRenamingTool)
    shadeRuntimeOnly(group = "dev.su5ed.sinytra", name = "sponge-mixin", version = versionMixin) { isTransitive = false }
    annotationProcessor(group = "dev.su5ed.sinytra", name = "sponge-mixin", version = versionMixin)

    compileOnly(group = "dev.su5ed.sinytra.fabric-api", name = "fabric-api", version = versionFabricApi)
    runtimeOnly(fg.deobf("dev.su5ed.sinytra.fabric-api:fabric-api:$versionFabricApi"))

    "languageCompileOnly"(sourceSets.main.get().output)
    "modCompileOnly"(sourceSets.main.get().output)
}

tasks {
    jar {
        finalizedBy("reobfJar")

        manifest {
            attributes(
                "Specification-Title" to project.name,
                "Specification-Vendor" to "Sinytra",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Sinytra",
                "Implementation-Timestamp" to LocalDateTime.now()
            )
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8" // Use the UTF-8 charset for Java compilation
    }

    assemble {
        dependsOn("reobfModJar", fullJar)
    }

    configureEach {
        if (name == "prepareRuns") {
            dependsOn(fullJar)
        }
        if (name == "addMixinsToJar") {
            enabled = false
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            suppressAllPomMetadataWarnings()

            from(components["java"])
        }
    }
}
