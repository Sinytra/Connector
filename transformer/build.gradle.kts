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
    mavenLocal()
}

dependencies {
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.24.3"))
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    implementation("org.sinytra.adapter:core:$versionAdapterCore")
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

/*
    // parameter  instance
    // parameter  item
    // parameter  original
  @Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;(method={"useItemOn"}, at={@Lorg/spongepowered/asm/mixin/injection/At;(value="INVOKE", target="Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")})
   L0
    LINENUMBER 25 L0
    ALOAD 3
    ICONST_2
    ANEWARRAY java/lang/Object
    DUP
    ICONST_0
    ALOAD 1
    AASTORE
    DUP
    ICONST_1
    ALOAD 2
    AASTORE
    INVOKEINTERFACE com/llamalad7/mixinextras/injector/wrapoperation/Operation.call ([Ljava/lang/Object;)Ljava/lang/Object; (itf)
    CHECKCAST java/lang/Boolean
    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
    IFNE L1
    ALOAD 2
    GETSTATIC net/minecraft/world/item/Items.SHEARS : Lnet/minecraft/world/item/Item;
    IF_ACMPNE L2
    ALOAD 1
    INVOKESTATIC org/betterx/bclib/items/tool/BaseShearsItem.isShear (Lnet/minecraft/world/item/ItemStack;)Z
    IFEQ L2
   L1
   FRAME SAME
    ICONST_1
    GOTO L3
   L2
   FRAME SAME
    ICONST_0
   L3
   FRAME SAME1 I
    IRETURN
   L4
    LOCALVARIABLE this Lorg/betterx/bclib/mixin/common/shears/PumpkinBlockMixin; L0 L4 0
    LOCALVARIABLE instance Lnet/minecraft/world/item/ItemStack; L0 L4 1
    LOCALVARIABLE item Lnet/minecraft/world/item/Item; L0 L4 2
    LOCALVARIABLE original Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation; L0 L4 3
    // signature Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Ljava/lang/Boolean;>;
    // declaration: original extends com.llamalad7.mixinextras.injector.wrapoperation.Operation<java.lang.Boolean>
    MAXSTACK = 5
    MAXLOCALS = 4

 */