import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.modmuss50.mpp.ReleaseType
import net.neoforged.moddevgradle.dsl.RunModel
import java.time.LocalDateTime

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(group = "org.yaml", name = "snakeyaml", version = "2.2")
    }
}

plugins {
    java
    `maven-publish`
    id("net.neoforged.moddev") version "0.1.126"
    id("io.github.goooler.shadow") version "8.1.8" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.5.+"
    id("net.neoforged.gradleutils") version "3.0.0"
    id("org.sinytra.adapter.userdev") version "1.0-SNAPSHOT"
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

val shade: Configuration by configurations.creating {
    isTransitive = false
    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
}
val legacyClasspath: Configuration by configurations.creating { isTransitive = false }
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
        extendsFrom(/*configurations.minecraft.get(), */shade)
    }
    
    additionalRuntimeClasspath {
        extendsFrom(legacyClasspath)
    }
}

println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
neoForge {
    // Specify the version of NeoForge to use.
    version = versionNeoForge
    neoFormRuntime.version.set("0.1.70") // TODO TEMP

    accessTransformers.add("src/mod/resources/META-INF/accesstransformer.cfg")

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
            systemProperty("connector.clean.path", tasks.createCleanArtifact.get().outputFile.get().asFile.absolutePath)
//            systemProperty("connector.cache.enabled", "false")
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

//        create("testModClient") {
//            client()
//            mods {
//                create("testconnector") {
//                    sourceSet(test)
//                }
//            }
//            programArguments.addAll("--mixin.config", "connectortest.mixins.json", "--quickPlaySingleplayer", "ctest")
//            gameDirectory.set(layout.projectDirectory.dir("run/test"))
//        }
    }
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net")
    }
    maven {
        name = "Sinytra"
        url = uri("https://maven.su5ed.dev/releases")
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
    legacyClasspath(group = "org.sinytra", name = "forgified-fabric-loader", version = versionForgifiedFabricLoader, classifier = "full")
    legacyClasspath(shade(group = "net.fabricmc", name = "access-widener", version = versionAccessWidener))
    legacyClasspath(shade(group = "org.sinytra", name = "ForgeAutoRenamingTool", version = versionForgeAutoRenamingTool))
    legacyClasspath(shade(group = "org.sinytra.adapter", name = "definition", version = versionAdapterDefinition) { isTransitive = false })
    adapterData(group = "org.sinytra.adapter", name = "adapter", version = versionAdapter)

    jarJar(implementation(group = "org.sinytra.adapter", name = "runtime", version = versionAdapterRuntime))
    "modImplementation"(implementation(group = "org.sinytra.forgified-fabric-api", name = "forgified-fabric-api", version = versionForgifiedFabricApi)) {
        exclude(group = "org.sinytra", module = "forgified-fabric-loader")
    }

    "modCompileOnly"(sourceSets.main.get().output)

    additionalRuntimeClasspath(files(tasks.jar))
    attributesSchema.getMatchingStrategy(Bundling.BUNDLING_ATTRIBUTE).compatibilityRules.add(BundlingCompatRule::class)
}

// We need fabric loader to be present on the service layer. In order to do that, we use shadow jar to ship it ourselves.
// However, this easily creates conflicts with other mods that might be providing it via JarInJar. To avoid this conflict,
// we provide a dummy nested jar with the same identifier, but a "max" version, so that it always takes priority over
// nested jars shipped by other mods, effectively disabling them.
val dummyFabricLoaderVersion = "999.999.999"
// This is the actualy dummy jar, set to a LIBRARY type to be put on the PLUGIN layer
val dummyFabricLoaderLangJar: Jar by tasks.creating(Jar::class) {
    manifest.attributes(
        "FMLModType" to "LIBRARY",
        "Implementation-Version" to dummyFabricLoaderVersion
    )
    archiveClassifier.set("fabricloader")
}
localJarJar("dummyForgifiedFabridLoaderConfig", "org.sinytra:forgified-fabric-loader", dummyFabricLoaderVersion, dummyFabricLoaderLangJar)

val modJar: Jar by tasks.creating(Jar::class) {
    from(mod.output)
    manifest.attributes("Implementation-Version" to project.version)
    archiveClassifier.set("mod")
}
localJarJar("modJarConfig", "org.sinytra:connector-mod", project.version.toString(), modJar)

val depsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(shade)

    exclude("assets/fabricloader/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.RSA")
    exclude("META-INF/maven/**")
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
val fullJar: ShadowJar by tasks.creating(ShadowJar::class) {
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

//
//    val modDownload = register("resolveTestMods") {
//        doFirst {
//            val configFile = rootProject.file("testmods.yaml")
//            val data: List<Map<String, String>> = configFile.reader().use(Yaml()::load)
//            val deps = data.map { project.dependencies.create(it["maven"] as String) }
//
//            val config = configurations.detachedConfiguration(*deps.toTypedArray())
//            val files = config.resolve()
//
//            val dir = project.file("run/test/mods").apply {
//                if (exists()) deleteRecursively()
//                mkdirs()
//            }
//            files.forEach { it.copyTo(dir.resolve(it.name)) }
//        }
//    }
//
//    configureEach {
//        if (name == "prepareRuns") {
//            dependsOn(fullJar)
//        }
//        if (name == "addMixinsToJar") {
//            enabled = false
//        }
//        if (name == "reobfModJar") {
//            mustRunAfter(modJar)
//        }
//        if (name == "runTestModClient") {
//            dependsOn(modDownload)
//        }
//        if (name == "downloadAssets" && providers.environmentVariable("CI").isPresent) {
//            enabled = false
//            (this as DownloadAssets).output.mkdirs()
//        }
//    }
}

publishMods {
    file.set(fullJar.archiveFile)
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("# $version"))
    type.set(PUBLISH_RELEASE_TYPE.orElse("alpha").map(ReleaseType::of))
    modLoaders.add("forge")
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

class BundlingCompatRule : AttributeCompatibilityRule<Bundling> {
    override fun execute(t: CompatibilityCheckDetails<Bundling>) {
        if (t.consumerValue?.name == Bundling.SHADOWED) {
            t.compatible()
        }
    }
}