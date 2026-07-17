import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipOutputStream

plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow")
}

group = "org.sinytra.connector"
version = rootProject.version

val versionAdapterCore: String by rootProject
val versionAutoRenamingTool: String by rootProject
val versionForgifiedFabricLoader: String by rootProject
val versionClassTweaker: String by rootProject

val runner: SourceSet by sourceSets.creating {}
val shade: Configuration by configurations.creating

configurations {
    "runnerCompileClasspath" {
        extendsFrom(compileClasspath.get())
    }
}

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "Sinytra"
        url = uri("https://maven.sinytra.org")
        content {
            includeGroupAndSubgroups("org.sinytra")
        }
    }
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
    mavenLocal()
}

dependencies {
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.24.3"))
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    implementation("org.sinytra.adapter:core:$versionAdapterCore")
    implementation("org.sinytra:AutoRenamingTool:$versionAutoRenamingTool") {
        isTransitive = false
    }
    implementation("org.sinytra:forgified-fabric-loader:$versionForgifiedFabricLoader")
    implementation("net.fabricmc:class-tweaker:$versionClassTweaker") { isTransitive = false }

    compileOnly("org.jetbrains:annotations:13.0")
    implementation("com.mojang:logging:1.2.7")
    implementation("net.neoforged.fancymodloader:loader:11.0.13")
    implementation("net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7")

    "runnerImplementation"(sourceSets.main.get().output)
    shade("runnerImplementation"("info.picocli:picocli:4.7.7")!!)
    "runnerAnnotationProcessor"("info.picocli:picocli-codegen:4.7.7")
}

tasks {
    compileJava {
        options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "org.sinytra.connector.transformer.runner.cli.Main",
                "Implementation-Version" to project.version
            )
        }
    }

    shadowJar {
        configurations = project.configurations.named("runtimeClasspath").map { listOf(it, shade) }
        from(sourceSets.main.map { it.output }, runner.output)

        mergeServiceFiles()
        transform(Log4JConfigTransformer(runner.output.resourcesDir))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            suppressAllPomMetadataWarnings()

            from(components["java"])
        }
        create<MavenPublication>("shadow") {
            from(components["shadow"])
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

class Log4JConfigTransformer(private val resourcesDir: File?) : ResourceTransformer {
    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.name == "log4j2.xml" && element.file.parentFile != resourcesDir
    }

    override fun transform(context: TransformerContext) {}
    override fun hasTransformedResource(): Boolean = true
    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
}
