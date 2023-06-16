import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.IMappingFile.*
import net.minecraftforge.srgutils.INamedMappingFile
import org.apache.tools.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.time.LocalDateTime

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("org.spongepowered.mixin") version "0.7.+"
    id("dev.su5ed.yarndeobf") version "0.1.+"
}

version = "1.0"
group = "dev.su5ed.connector"

val versionMc: String by project
val versionFabricLoader: String by project
val versionAccessWidener: String by project

val language by sourceSets.registering
val mod: SourceSet by sourceSets.creating

val shade: Configuration by configurations.creating
val shadeRuntimeOnly: Configuration by configurations.creating
val commonMods: Configuration by configurations.creating

val depsJar: ShadowJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(shade, shadeRuntimeOnly)

    exclude("assets/fabricloader/**")
    exclude("META-INF/**")
    exclude("ui/**")
    exclude("*.json")
    exclude("module-info.class")

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
val createObfToMcp by tasks.registering(GenerateSRG::class) {
    notch = true
    srg.set(tasks.extractSrg.flatMap { it.output })
    mappings.set(minecraft.mappings)
    format.set(Format.TSRG)
}
// TODO Create intermediate only for prod
val createMappings by tasks.registering(ConvertSRGTask::class) {
    inputYarnMappings.set { configurations.yarnMappings.get().singleFile }
    inputSrgMappings.set(tasks.extractSrg.flatMap { it.output })
    inputMcpMappings.set(createObfToMcp.flatMap { it.output })
}
val fullJar: Jar by tasks.creating(Jar::class) {
    from(zipTree(remappedDepsJar.archiveFile))
    from(languageJar)
    from(modJar)
    from(createMappings.flatMap { it.outputFile }) { rename { "mappings.tsrg" } }
    manifest.attributes("Additional-Dependencies-Language" to languageJar.archiveFile.get().asFile.name)
    manifest.attributes("Additional-Dependencies-Mod" to modJar.archiveFile.get().asFile.name)

    archiveClassifier.set("full")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

mixin {
    add(mod, "mixins.connectormod.refmap.json")
}

configurations {
    compileOnly {
        extendsFrom(shade, commonMods)
    }

    "languageImplementation" {
        extendsFrom(configurations.minecraft.get(), shade)
    }

    "modImplementation" {
        extendsFrom(configurations.minecraft.get(), shade, commonMods)
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

    accessTransformer(file("src/mod/resources/META-INF/accesstransformer.cfg"))

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            property("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE")
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
    maven("https://maven.blamejared.com")
    mavenLocal()
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-45.0.64")
    yarnMappings(group = "net.fabricmc", name = "yarn", version = "1.19.4+build.2")

    shade(project(":fabric-loader")) {
        isTransitive = false
    }
    shade(group = "net.fabricmc", name = "access-widener", version = versionAccessWidener)
    // TODO Currently uses a local version with NPE fix on this line
    // https://github.com/MinecraftForge/ForgeAutoRenamingTool/blob/140befc9bf3e0ca5c8280c6d8e455ec01a916268/src/main/java/net/minecraftforge/fart/internal/EnhancedRemapper.java#L385
    shade(group = "net.minecraftforge", name = "ForgeAutoRenamingTool", version = "1.0.4")

    compileOnly("dev.su5ed.sinytra.fabric-api:ForgifiedFabricAPI:1.0")
    runtimeOnly(fg.deobf("dev.su5ed.sinytra.fabric-api:ForgifiedFabricAPI:1.0"))

    commonMods(yarnDeobf.deobf("net.fabricmc.fabric-api:fabric-api-base:0.4.9+e62f51a3ff"))
    commonMods(yarnDeobf.deobf("net.fabricmc.fabric-api:fabric-rendering-v1:2.1.3+504944c8f4"))

    "languageCompileOnly"(sourceSets.main.get().output)
    "modCompileOnly"(sourceSets.main.get().output)

    shadeRuntimeOnly("net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5") {
        isTransitive = false
    }
}

tasks {
    jar {
        finalizedBy("reobfJar")

        manifest {
            attributes(
                "Specification-Title" to project.name,
                "Specification-Vendor" to "Su5eD",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Su5eD",
                "Implementation-Timestamp" to LocalDateTime.now()
            )
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8" // Use the UTF-8 charset for Java compilation
    }

    configureEach {
        if (name == "prepareRuns") {
            dependsOn(fullJar)
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

open class ConvertSRGTask : DefaultTask() {
    @get:InputFile
    val inputYarnMappings: RegularFileProperty = project.objects.fileProperty()

    @get:InputFile
    val inputSrgMappings: RegularFileProperty = project.objects.fileProperty()

    @get:InputFile
    val inputMcpMappings: RegularFileProperty = project.objects.fileProperty()

    @get:OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty().convention(project.layout.buildDirectory.file("$name/output.tsrg"))

    private val parentCache: MutableMap<String, Iterable<String>> = mutableMapOf()

    @TaskAction
    fun execute() {
        val yarnMappings = ZipFile(inputYarnMappings.asFile.get()).use { zip ->
            val inputStream = zip.getInputStream(zip.getEntry("mappings/mappings.tiny"))
            INamedMappingFile.load(inputStream)
        }
        val obfToIntermediary = yarnMappings.getMap("official", "intermediary")
        val obfToYarn = yarnMappings.getMap("official", "named")
        val obfToSrg = load(inputSrgMappings.asFile.get())
        val obfToMcp = load(inputMcpMappings.asFile.get())
        val minecraftJoined = MavenArtifactDownloader.generate(project, "net.minecraft:joined:1.19.4", true)!!

        println("Found Minecraft artifact at ${minecraftJoined.absolutePath}")
        ZipFile(minecraftJoined).use { mc ->
            val builder = IMappingBuilder.create("srg", "mcp", "intermediary", "named")
            obfToSrg.classes.forEach { cls ->
                val mapClasses = arrayOf(obfToMcp, obfToIntermediary, obfToYarn).map { it.getClass(cls.original) }
                if (mapClasses.none { it == null }) {
                    val mapCls = builder.addClass(cls.mapped, *mapClasses.map(INode::getMapped).toTypedArray())
                    cls.methods.forEach { method ->
                        mapCls.method(
                            obfToSrg.remapDescriptor(method.descriptor), method.mapped,
                            *mapClasses.map {
                                it.getMethod(method.original, method.descriptor)?.mapped ?: inheritMethod(mc, obfToSrg, cls, method)
                            }.toTypedArray()
                        )
                    }
                    cls.fields.forEach { field ->
                        mapCls.field(
                            field.mapped,
                            *mapClasses.map {
                                it.getField(field.original)?.mapped ?: inheritField(mc, obfToSrg, cls, field)
                            }.toTypedArray()
                        )
                    }
                } else {
                    project.logger.info("Ignoring mapping for class ${cls.original}")
                }
            }
            builder.build().write(outputFile.get().asFile.toPath(), Format.TSRG2)
        }
    }

    private fun inheritMethod(mc: ZipFile, mapping: IMappingFile, cls: IClass, mtd: IMethod): String {
        return if (mtd.original == "<init>" || mtd.original == "<clinit>") mtd.mapped
        else runParentLookup(mc, mapping, cls, mtd, ::lookupParentMethod)
            ?: throw RuntimeException("Method ${mtd.original} not found in ${cls.mapped}")
    }

    private fun <T> runParentLookup(mc: ZipFile, mapping: IMappingFile, cls: IClass, mtd: T, processor: (ZipFile, IMappingFile, String, T) -> String?): String? =
        lookupParents(mc, cls.original)
            .mapNotNull { myParent -> processor(mc, mapping, myParent, mtd) }
            .firstOrNull()

    private fun lookupParentMethod(mc: ZipFile, mapping: IMappingFile, parent: String, mtd: IMethod): String? {
        if (!parent.startsWith("net/minecraft") && (parent.startsWith("com/mojang/serialization") || parent.startsWith("com/mojang/brigadier") || !parent.startsWith("com/mojang/") || parent.startsWith("com/mojang/datafixers/"))) {
            return mtd.mapped
        }
        val parentCls = mapping.getClass(parent)
        val parentMethod = parentCls.getMethod(mtd.original, mtd.descriptor)
        return parentMethod?.mapped ?: runParentLookup(mc, mapping, parentCls, mtd, ::lookupParentMethod)
    }

    private fun inheritField(mc: ZipFile, mapping: IMappingFile, cls: IClass, fd: IField): String {
        return runParentLookup(mc, mapping, cls, fd, ::lookupParentField)
            ?: throw RuntimeException("Field ${fd.original} not found in ${cls.mapped}")
    }

    private fun lookupParentField(mc: ZipFile, mapping: IMappingFile, parent: String, fd: IField): String? {
        if (!parent.startsWith("net/minecraft") && !parent.startsWith("com/mojang/") || parent.startsWith("java/") || parent.startsWith("jdk/") || parent.startsWith("com/mojang/datafixers/")) {
            return fd.mapped
        }
        val parentCls = mapping.getClass(parent)
        val parentField = parentCls.getField(fd.original)
        return parentField?.mapped ?: runParentLookup(mc, mapping, parentCls, fd, ::lookupParentField)
    }

    private fun lookupParents(zip: ZipFile, name: String): Iterable<String> {
        val cls = zip.getEntry("${name}.class")
        return parentCache.computeIfAbsent(name) {
            zip.getInputStream(cls).use { ins ->
                val reader = ClassReader(ins)
                ClassNode().run {
                    reader.accept(this, 0)
                    (superName?.let(::setOf) ?: emptySet()) + interfaces
                }
            }
        }
    }
}
