plugins {
    kotlin("jvm") version "1.8.21"
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net/")
    }
}

dependencies {
    compileOnly(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = "5.1.+")
    compileOnly("net.minecraftforge:artifactural:3.0.14")
    implementation("net.minecraftforge:srgutils:0.4.+")
    implementation("commons-io:commons-io:2.12.0")
}

gradlePlugin {
    plugins {
        create("yarn-remapper") {
            id = "dev.su5ed.connector.yarn-remapper"
            implementationClass = "dev.su5ed.connector.remapper.YarnMapperPlugin"
        }
    }
}
