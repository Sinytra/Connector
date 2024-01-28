import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.modmuss50.mpp.ReleaseType
import net.minecraftforge.gradle.common.tasks.DownloadAssets
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.tasks.JarJar
import net.minecraftforge.gradle.userdev.util.MavenPomUtils
import net.minecraftforge.jarjar.metadata.ContainedJarIdentifier
import net.minecraftforge.jarjar.metadata.ContainedJarMetadata
import net.minecraftforge.jarjar.metadata.ContainedVersion
import net.minecraftforge.jarjar.metadata.MetadataIOHandler
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.artifact.versioning.VersionRange
import org.yaml.snakeyaml.Yaml
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import kotlin.io.path.inputStream

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
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("org.spongepowered.mixin") version "0.7.+"
    id("me.modmuss50.mod-publish-plugin") version "0.3.+"
    id("net.neoforged.gradleutils") version "2.0.+"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
}

val versionConnector: String by project
val versionAdapter: String by project
val versionAdapterDefinition: String by project
val versionMc: String by project
val versionForge: String by project
val versionForgeAutoRenamingTool: String by project
val versionFabricLoader: String by project
val versionAccessWidener: String by project
val versionFabricApi: String by project
val versionMixin: String by project
val versionMixinTransmog: String by project
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

group = "dev.su5ed.sinytra"
version = "$versionConnector+$versionMc"
// Append git commit hash for dev versions
if (!PUBLISH_RELEASE_TYPE.isPresent) {
    version = "$version+dev-${gradleutils.gitInfo["hash"]}"
}
println("Project version: $version")

val mod: SourceSet by sourceSets.creating
val test: SourceSet by sourceSets

val shade: Configuration by configurations.creating { isTransitive = false }
val adapterData: Configuration by configurations.creating

val depsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(shade)

    exclude("assets/fabricloader/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.RSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/services/net.minecraftforge.forgespi.language.IModLanguageProvider")
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
val modJar: JarJar by tasks.creating(JarJar::class) {
    dependsOn(dummyFabricLoaderLangJar)
    from(mod.output)
    inputs.file(dummyFabricLoaderLangJar.archiveFile)
    configurations = listOf(project.configurations.jarJar.get())
    (includedDependencies as ConfigurableFileCollection).from(dummyFabricLoaderLangJar)
    doLast {
        FileSystems.newFileSystem(archiveFile.get().asFile.toPath()).use { fs ->
            val jarPath = "META-INF/jarjar/" + dummyFabricLoaderLangJar.archiveFile.get().asFile.name
            val metadataPath = fs.getPath("META-INF/jarjar/metadata.json")
            val metadata = metadataPath.inputStream().use { ins -> MetadataIOHandler.fromStream(ins).orElseThrow() }
            metadata.jars() += ContainedJarMetadata(
                ContainedJarIdentifier("dev.su5ed.sinytra", "fabric-loader"),
                ContainedVersion(VersionRange.createFromVersion("[$dummyFabricLoaderVersion,)"), DefaultArtifactVersion(dummyFabricLoaderVersion)),
                jarPath,
                false
            )
            Files.deleteIfExists(metadataPath)
            Files.write(metadataPath, MetadataIOHandler.toLines(metadata), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        }
    }
    manifest.attributes(
        "Implementation-Version" to project.version,
        "MixinConfigs" to "connectormod.mixins.json"
    )
    archiveClassifier.set("mod")
}
val remappedDepsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    dependsOn(depsJar)

    from(tasks.jar)
    from(depsJar)
    mergeServiceFiles() // Relocate services
    relocate("net.minecraftforge.fart", "reloc.net.minecraftforge.fart")
    relocate("net.minecraftforge.srgutils", "reloc.net.minecraftforge.srgutils")
    relocate("net.fabricmc.accesswidener", "reloc.net.fabricmc.accesswidener")
    relocate("org.sat4j", "reloc.org.sat4j")
    relocate("net.bytebuddy", "reloc.net.bytebuddy")
    archiveClassifier.set("deps-reloc")
}
val fullJar: Jar by tasks.creating(Jar::class) {
    mustRunAfter("reobfModJar")
    from(zipTree(remappedDepsJar.archiveFile))
    from(zipTree(provider { adapterData.singleFile })) {
        into("adapter_data")
        include("*.json")
    }
    // Despite not being part of jarjar metadata, the mod jar must be located in this directory
    // in order to be deobfuscated by FG in userdev environments
    into("META-INF/jarjar/") {
        from(modJar)
    }
    manifest {
        from(tasks.jar.get().manifest)
        attributes("Embedded-Dependencies-Mod" to "META-INF/jarjar/" + modJar.archiveFile.get().asFile.name)
        attributes("Fabric-Loader-Version" to versionFabricLoader.split("+")[1])
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

configurations {
    setOf(runtimeElements, apiElements).forEach { conf ->
        conf.configure {
            outgoing {
                artifacts.clear()
                artifact(fullJar)
            }
        }
    }
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

repositories {
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://cursemaven.com")
            }
        }
        forRepositories(fg.repository)
        filter {
            includeGroup("curse.maven")
        }
    }
}

sourceSets {
    main {
        runtimeClasspath = runtimeClasspath.minus(output).plus(files(fullJar))
    }
}

println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
minecraft {
    mappings("parchment", "2023.06.26-1.20.1")
    accessTransformer(file("src/mod/resources/META-INF/accesstransformer.cfg"))

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            property("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE,COREMOD")
            property("connector.logging.markers", "MIXINPATCH,MERGER")
            property("mixin.debug.export", "true")
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

        val clientConfig = create("client", config)
        create("server", config)

        create("testModClient") {
            mods {
                create("testconnector") {
                    sources(test)
                }
            }
            args("--mixin.config", "connectortest.mixins.json")
            args("--quickPlaySingleplayer", "ctest")
            parent(clientConfig)
            workingDirectory = project.file("run/test").canonicalPath
        }
    }
}

// Attach clean artifact path to runs
val CLEAN_ARTIFACT = "net.minecraft:joined:%s:srg"
afterEvaluate {
    val mcpVersion = project.extra["MCP_VERSION"]
    val cleanArtifactJar = MavenArtifactDownloader.generate(project, CLEAN_ARTIFACT.format(mcpVersion), true) ?: throw RuntimeException("Cannot find clean minecraft artifact")
    minecraft.runs.configureEach {
        property("connector.clean.path", cleanArtifactJar)
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
    exclusiveRepo("https://api.modrinth.com/maven/", "maven.modrinth")
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    mavenLocal()
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-$versionForge")

    shade(group = "dev.su5ed.sinytra", name = "fabric-loader", version = versionFabricLoader) { isTransitive = false }
    // Fabric loader dependencies
    shade(group = "org.ow2.sat4j", name = "org.ow2.sat4j.core", version = "2.3.6")
    shade(group = "org.ow2.sat4j", name = "org.ow2.sat4j.pb", version = "2.3.6")
    shade(group = "net.minecraftforge", name = "srgutils", version = "0.5.4")
    shade(group = "net.fabricmc", name = "access-widener", version = versionAccessWidener)
    shade(group = "dev.su5ed.sinytra", name = "ForgeAutoRenamingTool", version = versionForgeAutoRenamingTool)
    shade(group = "dev.su5ed.sinytra.adapter", name = "definition", version = versionAdapterDefinition) { isTransitive = false }
    shade(group = "io.github.steelwoolmc", name = "mixin-transmogrifier", version = versionMixinTransmog)
    adapterData(group = "dev.su5ed.sinytra.adapter", name = "adapter", version = versionAdapter)

    annotationProcessor(group = "net.fabricmc", name = "sponge-mixin", version = versionMixin)
    compileOnly(group = "net.fabricmc", name = "sponge-mixin", version = versionMixin)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:${mixinextrasVersion}")!!) {
        jarJar.ranged(this, "[${mixinextrasVersion},)")
    }
    compileOnly(group = "dev.su5ed.sinytra.fabric-api", name = "fabric-api", version = versionFabricApi)
    runtimeOnly(fg.deobf("dev.su5ed.sinytra.fabric-api:fabric-api:$versionFabricApi"))

    "modCompileOnly"(sourceSets.main.get().output)
    "modCompileOnly"("io.github.llamalad7:mixinextras-common:${mixinextrasVersion}")

    runtimeOnly(fg.deobf("curse.maven:connector-extras-913445:5027683"))
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
                "Implementation-Timestamp" to LocalDateTime.now(),
                "Automatic-Module-Name" to "dev.su5ed.sinytra.connector"
            )
        }

        archiveClassifier.set("slim")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8" // Use the UTF-8 charset for Java compilation
    }

    assemble {
        dependsOn("reobfModJar", fullJar)
    }

    val modDownload = register("resolveTestMods") {
        doFirst {
            val configFile = rootProject.file("testmods.yaml")
            val data: List<Map<String, String>> = configFile.reader().use(Yaml()::load)
            val deps = data.map { project.dependencies.create(it["maven"] as String) }

            val config = configurations.detachedConfiguration(*deps.toTypedArray())
            val files = config.resolve()

            val dir = project.file("run/test/mods").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            files.forEach { it.copyTo(dir.resolve(it.name)) }
        }
    }

    configureEach {
        if (name == "prepareRuns") {
            dependsOn(fullJar)
        }
        if (name == "addMixinsToJar") {
            enabled = false
        }
        if (name == "reobfModJar") {
            mustRunAfter(modJar)
        }
        if (name == "runTestModClient") {
            dependsOn(modDownload)
        }
        if (name == "downloadAssets" && providers.environmentVariable("CI").isPresent) {
            enabled = false
            (this as DownloadAssets).output.mkdirs()
        }
    }
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
            fg.component(this)
            pom.withXml {
                asNode().remove(MavenPomUtils.getDependenciesNode(this))
            }
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

// Adapted from https://gist.github.com/pupnewfster/6c21401789ca6d74f9892be8c1c505c9
fun RepositoryHandler.exclusiveRepo(location: String, vararg groups: String) {
    exclusiveRepo(location) {
        for (group in groups) {
            includeGroup(group)
        }
    }
}

fun RepositoryHandler.exclusiveRepo(location: String, config: Action<InclusiveRepositoryContentDescriptor>) {
    exclusiveContent {
        forRepositories(maven(location), fg.repository)
        filter(config)
    }
}