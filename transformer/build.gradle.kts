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

val versionAdapter: String by rootProject
val versionAdapterDefinition: String by rootProject
val versionForgeAutoRenamingTool: String by rootProject
val versionForgifiedFabricLoader: String by rootProject
val versionAccessWidener: String by rootProject

val runner: SourceSet by sourceSets.creating {}
val shade: Configuration by configurations.creating

configurations {
    "runnerCompileClasspath" {
        extendsFrom(compileClasspath.get())
    }
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
}

dependencies {
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.24.3"))
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    implementation("org.sinytra.adapter:definition:$versionAdapterDefinition")
    implementation("org.sinytra.adapter:adapter:$versionAdapter")
    implementation("org.sinytra:ForgeAutoRenamingTool:$versionForgeAutoRenamingTool") {
        isTransitive = false
    }
    implementation("org.sinytra:forgified-fabric-loader:$versionForgifiedFabricLoader")
    implementation("net.fabricmc:access-widener:$versionAccessWidener")

    compileOnly("org.jetbrains:annotations:13.0")
    implementation("com.mojang:logging:1.2.7")
    implementation("cpw.mods:modlauncher:11.0.4")
    implementation("cpw.mods:securejarhandler:3.0.8")
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

        manifest.inheritFrom(jar.get().manifest)
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
}

class Log4JConfigTransformer(private val resourcesDir: File?) : ResourceTransformer {
    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.name == "log4j2.xml" && element.file.parentFile != resourcesDir
    }

    override fun transform(context: TransformerContext) {}
    override fun hasTransformedResource(): Boolean = true
    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
}