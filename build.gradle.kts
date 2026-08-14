import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.modmuss50.mpp.ReleaseType

plugins {
    java
    `maven-publish`
    alias(libs.plugins.moddev)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.mod.publish)
    alias(libs.plugins.gradleutils)
    alias(libs.plugins.wiki.toolkit)
}

val versionConnector = project.property("versionConnector") as String
val versionMc = libs.versions.minecraft.get()

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
    version = libs.versions.neoforge.get()

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

//            logLevel = org.slf4j.event.Level.DEBUG
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
    shade(libs.adapter.core) { isTransitive = false }
    shade(libs.classtweaker) { isTransitive = false }
    shade(libs.auto.renaming.tool) { isTransitive = false }
    shade(project(":transformer")) { isTransitive = false }

    implementation(libs.adapter.core) { isTransitive = false }
    implementation(libs.classtweaker) { isTransitive = false }
    implementation(libs.auto.renaming.tool) { isTransitive = false }
    api(project(":transformer"))
    implementation(project(":transformer")) { isTransitive = false }

    implementation(libs.adapter.runtime)
    jarJar(libs.adapter.runtime)

    implementation(libs.launchpad)
    "modImplementation"(libs.launchpad)

    implementation(libs.forgified.fabric.api) {
        exclude(group = "org.sinytra", module = "forgified-fabric-loader")
    }
    "modImplementation"(libs.forgified.fabric.api) {
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
        from("src/mod/resources/META-INF/neoforge.mods.toml") {
            into("META-INF")
        }
        from("src/mod/resources/logo.png")

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
