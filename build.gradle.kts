import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.modmuss50.mpp.ReleaseType
import net.neoforged.moddevgradle.dsl.RunModel
import java.time.LocalDateTime

plugins {
    java
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.1-beta"
    id("io.github.goooler.shadow") version "8.1.8" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.5.+"
    id("net.neoforged.gradleutils") version "3.0.0"
    id("org.sinytra.adapter.userdev") version "1.2-SNAPSHOT"
}

val versionConnector: String by project
val versionAdapter: String by project
val versionAdapterDefinition: String by project
val versionAdapterRuntime: String by project
val versionMc: String by project
val versionNeoForge: String by project
val versionParchment: String by project
val versionForgeAutoRenamingTool: String by project
val versionForgifiedFabricLoader: String by project
val versionAccessWidener: String by project
val versionForgifiedFabricApi: String by project
val curseForgeId: String by project
val modrinthId: String by project
val githubRepository: String by project
val publishBranch: String by project
val forgifiedFabricApiCurseForge: String by project
val forgifiedFabricApiModrinth: String by project
val connectorExtrasCurseForge: String by project
val connectorExtrasModrinth: String by project
val mixinextrasVersion: String by project

val PUBLISH_RELEASE_TYPE: Provider<String> = providers.environmentVariable("PUBLISH_RELEASE_TYPE")

group = "org.sinytra"
version = "$versionConnector+$versionMc"
// Append git commit hash for dev versions
if (!PUBLISH_RELEASE_TYPE.isPresent) {
    version = "$version+dev-${gradleutils.gitInfo["hash"]}"
}
logger.lifecycle("Project version: $version")

val mod: SourceSet by sourceSets.creating
val test: SourceSet by sourceSets

val shade: Configuration by configurations.creating
val adapterData: Configuration by configurations.creating

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

configurations {
    compileOnly {
        extendsFrom(shade)
    }

    "modCompileOnly" {
        extendsFrom(configurations.compileOnly.get())
    }

    "modImplementation" {
        extendsFrom(shade)
    }
    
    additionalRuntimeClasspath {
        extendsFrom(shade)
    }
}

println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
neoForge {
    // Specify the version of NeoForge to use.
    version = versionNeoForge

    accessTransformers {
        from(project.file("src/mod/resources/META-INF/accesstransformer.cfg"))
    }

    parchment {
        mappingsVersion = versionParchment
        minecraftVersion = versionMc
    }

    runs {
        val config = Action<RunModel> {
            systemProperty("forge.logging.console.level", "debug")
            systemProperty("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE,COREMOD")
            systemProperty("connector.logging.markers", "MIXINPATCH,MERGER")
            systemProperty("mixin.debug.export", "true")
            gameDirectory.set(layout.projectDirectory.dir("run"))

            mods {
                maybeCreate("connector").apply {
                    sourceSet(mod)
                }
            }
        }

        create("client") {
            client()
            config(this)
        }
        create("server") {
            server()
            config(this)
        }
    }
}

repositories {
    maven {
        name = "Sinytra"
        url = uri("https://maven.su5ed.dev/releases")
        content {
            includeGroupAndSubgroups("org.sinytra")
        }
    }
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
}

dependencies {
    shade(group = "org.sinytra", name = "forgified-fabric-loader", version = versionForgifiedFabricLoader)
    shade(group = "net.fabricmc", name = "access-widener", version = versionAccessWidener) { isTransitive = false }
    shade(group = "org.sinytra", name = "ForgeAutoRenamingTool", version = versionForgeAutoRenamingTool) { isTransitive = false }
    shade(group = "org.sinytra.adapter", name = "definition", version = versionAdapterDefinition) { isTransitive = false }
    adapterData(group = "org.sinytra.adapter", name = "adapter", version = versionAdapter)

    jarJar(implementation(group = "org.sinytra.adapter", name = "runtime", version = versionAdapterRuntime))
    "modImplementation"(implementation(group = "org.sinytra.forgified-fabric-api", name = "forgified-fabric-api", version = versionForgifiedFabricApi)) {
        exclude(group = "org.sinytra", module = "forgified-fabric-loader")
    }

    "modCompileOnly"(sourceSets.main.get().output)

    additionalRuntimeClasspath(files(tasks.jar))
}

val modJar: Jar by tasks.creating(Jar::class) {
    from(mod.output)
    manifest.attributes("Implementation-Version" to project.version)
    archiveClassifier.set("mod")
}
localJarJar("modJarConfig", "org.sinytra:connector-mod", project.version.toString(), modJar)

val depsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(shade)

    exclude(
        "assets/fabricloader/**",
        "META-INF/*.SF", "META-INF/*.RSA",
        "META-INF/maven/**", "META-INF/jars/**", "META-INF/jarjar/**"
    )
    exclude("META-INF/services/net.neoforged.neoforgespi.language.IModLanguageLoader")
    exclude("ui/**")
    exclude("*.json", "*.html", "*.version")
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

val fullJar by tasks.registering(ShadowJar::class) {
    from(tasks.jar, depsJar)
    mergeServiceFiles() // Relocate services
    relocate("net.minecraftforge.fart", "reloc.net.minecraftforge.fart")
    relocate("net.minecraftforge.srgutils", "reloc.net.minecraftforge.srgutils")
    relocate("net.fabricmc.accesswidener", "reloc.net.fabricmc.accesswidener")
    relocate("org.sat4j", "reloc.org.sat4j")
    relocate("net.bytebuddy", "reloc.net.bytebuddy")
    manifest.attributes(tasks.jar.get().manifest.attributes)
    archiveClassifier.set("full")
}

tasks {
    jar {
        from(zipTree(provider { adapterData.singleFile })) {
            into("adapter_data")
            include("*.json")
        }
        manifest {
            attributes(
                "Specification-Title" to project.name,
                "Specification-Vendor" to "Sinytra",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Sinytra",
                "Implementation-Timestamp" to LocalDateTime.now(),
                "Automatic-Module-Name" to "org.sinytra.connector",
                "Fabric-Loader-Version" to versionForgifiedFabricLoader.split("+")[1]
            )
        }
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    assemble {
        dependsOn(fullJar)
    }
}

publishMods {
    file.set(fullJar.flatMap { it.archiveFile })
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("# $version"))
    type.set(PUBLISH_RELEASE_TYPE.orElse("alpha").map(ReleaseType::of))
    modLoaders.add("neoforge")
    dryRun.set(!providers.environmentVariable("CI").isPresent)

    github {
        accessToken.set(providers.environmentVariable("GITHUB_TOKEN"))
        repository.set(githubRepository)
        commitish.set(publishBranch)
    }
    curseforge {
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
        projectId.set(curseForgeId)
        minecraftVersions.add(versionMc)
        requires {
            slug.set(forgifiedFabricApiCurseForge)
        }
        optional {
            slug.set(connectorExtrasCurseForge)
        }
    }
    modrinth {
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        projectId.set(modrinthId)
        minecraftVersions.add(versionMc)
        requires {
            id.set(forgifiedFabricApiModrinth)
        }
        optional {
            id.set(connectorExtrasModrinth)
        }
    }
}

configurations.runtimeElements {
    setExtendsFrom(emptySet())
    outgoing {
        artifacts.clear()
        artifact(fullJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            suppressAllPomMetadataWarnings()

            from(components["java"])
        }
    }
    repositories {
        val env = System.getenv()
        if (env["MAVEN_URL"] != null) {
            repositories.maven {
                url = uri(env["MAVEN_URL"] as String)
                if (env["MAVEN_USERNAME"] != null) {
                    credentials {
                        username = env["MAVEN_USERNAME"]
                        password = env["MAVEN_PASSWORD"]
                    }
                }
            }
        }
    }
}

fun localJarJar(configName: String, mavenCoords: String, version: String, artifact: Any) {
    configurations.create(configName) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
        }
        outgoing {
            artifact(artifact)
            capability("$mavenCoords:$version")
        }
    }
    dependencies {
        jarJar(project(":")) { capabilities { requireCapability(mavenCoords) } }
    }
}
