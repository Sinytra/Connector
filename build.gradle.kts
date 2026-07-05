import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.modmuss50.mpp.ReleaseType
import org.slf4j.event.Level

plugins {
    java
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.141"
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("me.modmuss50.mod-publish-plugin") version "2.1.1"
    id("net.neoforged.gradleutils") version "5.1.1"
    id("org.moddedmc.wiki.toolkit") version "0.4.1"
}

val versionConnector = project.property("versionConnector") as String
val versionLaunchpad = project.property("versionLaunchpad") as String
val versionAdapterCore = project.property("versionAdapterCore") as String
val versionAdapterRuntime = project.property("versionAdapterRuntime") as String
val versionAutoRenamingTool = project.property("versionAutoRenamingTool") as String
val versionClassTweaker = project.property("versionClassTweaker") as String
val versionMc = project.property("versionMc") as String
val versionNeoForge = project.property("versionNeoForge") as String
val versionForgifiedFabricApi = project.property("versionForgifiedFabricApi") as String

val curseForgeId = project.property("curseForgeId") as String
val modrinthId = project.property("modrinthId") as String
val githubRepository = project.property("githubRepository") as String
val publishBranch = project.property("publishBranch") as String
val forgifiedFabricApiCurseForge = project.property("forgifiedFabricApiCurseForge") as String
val forgifiedFabricApiModrinth = project.property("forgifiedFabricApiModrinth") as String
val launchpadCurseForge = project.property("launchpadCurseForge") as String
val launchpadModrinth = project.property("launchpadModrinth") as String
val connectorExtrasCurseForge = project.property("connectorExtrasCurseForge") as String
val connectorExtrasModrinth = project.property("connectorExtrasModrinth") as String

val PUBLISH_RELEASE_TYPE: Provider<String> = providers.environmentVariable("PUBLISH_RELEASE_TYPE")

group = "org.sinytra"
version = "$versionConnector+$versionMc"
// Append git commit hash for dev versions
if (!PUBLISH_RELEASE_TYPE.isPresent) {
    version = "$version+dev-${gradleutils.gitInfo["hash"]}"
}
logger.lifecycle("Project version: $version")

val mod = sourceSets.create("mod")
val shade = configurations.create("shade")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
neoForge {
    version = versionNeoForge

    accessTransformers {
        from(project.file("src/mod/resources/META-INF/accesstransformer.cfg"))
    }

    runs {
        create("client") {
            client()
        }

        create("server") {
            server()
            programArgument("--nogui")
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE,COREMOD")
            systemProperty("connector.logging.markers", "MIXINPATCH,MERGER")
            systemProperty("mixin.debug.export", "true")

//            logLevel = Level.DEBUG
        }
    }

    mods {
        create("connector") {
            sourceSet(mod)
        }
    }

    addModdingDependenciesTo(mod)
}

repositories {
    mavenLocal()
    maven {
        name = "Sinytra"
        url = uri("https://maven.su5ed.dev/releases")
        content {
            includeGroupAndSubgroups("org.sinytra")
        }
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
}

dependencies {
    shade("org.sinytra.adapter:core:$versionAdapterCore") { isTransitive = false }
    shade("net.fabricmc:class-tweaker:$versionClassTweaker") { isTransitive = false }
    shade("org.sinytra:AutoRenamingTool:$versionAutoRenamingTool") { isTransitive = false }
    shade(project(":transformer")) { isTransitive = false }

    implementation("org.sinytra.adapter:core:$versionAdapterCore") { isTransitive = false }
    implementation("net.fabricmc:class-tweaker:$versionClassTweaker") { isTransitive = false }
    implementation("org.sinytra:AutoRenamingTool:$versionAutoRenamingTool") { isTransitive = false }
    implementation(project(":transformer")) { isTransitive = false }

    jarJar(implementation(group = "org.sinytra.adapter", name = "runtime", version = versionAdapterRuntime))
    "modImplementation"(implementation(group = "org.sinytra.launchpad", name = "launchpad", version = versionLaunchpad))
    "modImplementation"(implementation(group = "org.sinytra.forgified-fabric-api", name = "forgified-fabric-api", version = versionForgifiedFabricApi)) {
        exclude(group = "org.sinytra", module = "forgified-fabric-loader")
    }

    "modCompileOnly"(sourceSets.main.get().output)
}

val modJar = tasks.register("modJar", Jar::class) {
    from(mod.output)
    manifest.attributes("Implementation-Version" to project.version)
    archiveClassifier.set("mod")
}
localJarJar("modJarConfig", "org.sinytra:connector-mod", project.version.toString(), modJar)

val depsJar = tasks.register("depsJar", ShadowJar::class) {
    configurations = listOf(shade)

    exclude(
        "assets/fabricloader/**",
        "META-INF/*.SF", "META-INF/*.RSA",
        "META-INF/maven/**", "META-INF/jars/**", "META-INF/jarjar/**"
    )
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

val fullJar = tasks.register("fullHar", ShadowJar::class) {
    from(
        depsJar.flatMap { it.archiveFile.map(::zipTree) },
        tasks.jar.flatMap { it.archiveFile.map(::zipTree) }
    )

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.FAIL }

    manifest.attributes(tasks.jar.get().manifest.attributes)
    archiveClassifier.set("full")

    doLast {
        val githubOutput = System.getenv("GITHUB_OUTPUT")
        if (githubOutput != null) {
            File(githubOutput).appendText("PRIMARY_ARTIFACT=${archiveFile.get().asFile.absolutePath}")
        }
    }
}

tasks {
    jar {
        manifest {
            attributes(
                "Specification-Title" to project.name,
                "Specification-Vendor" to "Sinytra",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Sinytra",
                "Automatic-Module-Name" to "org.sinytra.connector",
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

val versionPattern = "(?<ver>[\\d.]+)-?(?<channel>beta)?\\.?(?<mod>\\d+)?\\+?(?<game>[\\d.]+)?(?:\\+(?<dev>dev)?-?(?<hash>.+)?)?".toRegex()

publishMods {
    file.set(fullJar.flatMap { it.archiveFile })
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("# $version"))
    type.set(PUBLISH_RELEASE_TYPE.orElse("alpha").map(ReleaseType::of))
    modLoaders.add("neoforge")
    dryRun.set(!providers.environmentVariable("CI").isPresent)
    displayName.set(version.map { s ->
        val matched = versionPattern.matchEntire(s) ?: throw IllegalArgumentException("Unexpected version format")
        val mainVer = matched.groups["ver"]?.value ?: throw IllegalArgumentException("Missing main version")
        val channelVer = matched.groups["channel"]?.value?.let { it + (matched.groups["mod"]?.value?.let { m -> " $m" } ?: "") } ?: ""
        val devHash = matched.groups["hash"]?.value?.let { "(dev $it)" } ?: ""
        "Connector $mainVer" + (if (channelVer.isNotEmpty()) " $channelVer" else "") + (if (devHash.isNotEmpty()) " $devHash" else "")
    })

    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = githubRepository
        commitish = publishBranch
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = curseForgeId
        minecraftVersions.add(versionMc)
        client = true
        server = true
        requires {
            slug = forgifiedFabricApiCurseForge
        }
        requires { 
            slug = launchpadCurseForge
        }
        optional {
            slug = connectorExtrasCurseForge
        }
    }
    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = modrinthId
        minecraftVersions.add(versionMc)
        requires {
            id = forgifiedFabricApiModrinth
        }
        requires { 
            id = launchpadModrinth
        }
        optional {
            id = connectorExtrasModrinth
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

wiki {
    docs {
        create("connector") {
            root = file("docs")
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
