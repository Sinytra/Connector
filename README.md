<p align="center">
  <img src="https://github.com/Sinytra/Connector/assets/51261569/80106b55-dbd3-43d3-a00e-17075f03bcba">
</p>
<p align="center">
  <a href="https://github.com/Sinytra/Connector/actions/workflows/build.yml"><img src="https://github.com/Sinytra/Connector/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/Sinytra/Connector/releases/latest"><img src="https://img.shields.io/github/v/release/Sinytra/Connector?style=flat&label=Release&include_prereleases&sort=semver"></a>
  <a href="https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector"><img src="https://cf.way2muchnoise.eu/title/sinytra-connector.svg"></a>
  <a href="https://modrinth.com/mod/connector"><img src="https://img.shields.io/modrinth/dt/u58R1TMW?color=00AF5C&label=modrinth&style=flat&logo=modrinth"></a>
  <a href="https://github.com/Sinytra/ForgifiedFabricAPI"><img src="https://raw.githubusercontent.com/Sinytra/.github/main/badges/forgified-fabric-api/compacter.svg"></a>
  <a href="https://discord.gg/mamk7z3TKZ"><img src="https://discordapp.com/api/guilds/1141048834177388746/widget.png?style=shield"></a>
  <a href="https://nightly.link/Sinytra/Connector/workflows/build/dev/Maven%20Local.zip"><img src="https://img.shields.io/badge/Nightly-Download-9a32f0?logo=github"></a>
</p>

> [!WARNING]  
> Connector is currently in beta state, and many mods may not work as expected, or might be broken completely.  
> Please refer to our [Mod Compatibility Thread](https://github.com/Sinytra/Connector/discussions/12) and
> [Issue Tracker](https://github.com/Sinytra/Connector/issues) for ongoing compatibility issues.  
> When reporting bugs, please make sure you are using the latest release of Connector and Forgified Fabric API,
> as well as **Forge 47.1.3** on Minecraft 1.20.1

## 📖 About

**Sinytra Connector** is a translation/compatibility layer that allows running [Fabric](https://fabricmc.net) mods
on [MinecraftForge](https://minecraftforge.net). Its goal is to bring the two platforms closer together, saving
developers time and effort maintaining their mods for multiple platforms at once, as well as allowing players to play
all their favourite mods in one modpack.

**📘 The official documentation is available at [sinytra.org](https://sinytra.org/docs).**

### 🔗 Related Projects

- Visit the [Mod Compatibility Thread](https://github.com/Sinytra/Connector/discussions/12) to can find information about known working / incompatible mods
- To learn more about how Connector works, read our [Introductory blog post](https://github.com/Sinytra/Connector/discussions/11)
- Developing cross-platform mods? Check out the [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI), a port of the Fabric API to Forge
- Install [Connector Extras](https://github.com/Sinytra/ConnectorExtras) for improved compatibility with third-party libraries and APIs
- If you're using Embeddium with Fabric mods installed, also install [Lazurite](https://modrinth.com/mod/lazurite) for proper FRAPI compatibility

### 💬 Join the Community

We have an official [Discord community](https://discord.gg/mamk7z3TKZ) for Connector. By joining, you can:

- Get help and technical support from our team and community members
- Keep in touch with the latest development updates and community events
- Engage in the project's development and collaborate with our team
- ... and just hang out with the rest of our community.

## 📋 Usage Guide

To install Connector and its dependencies, follow the same installation steps as you would for any other mods:

1. Install **Minecraft Forge**. For Minecraft 1.20.1, it is recommended to use version **`47.1.3`**.  
[\[Minecraft Forge website\]](https://files.minecraftforge.net)
2. Install **Connector**. Get the latest release from one of our official distribution channels and drop the jar in your mods folder.  
[\[CurseForge\]](https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector) [\[Modrinth\]](https://modrinth.com/mod/connector) [\[GitHub\]](https://github.com/Sinytra/Connector/releases)
4. Download the **Forgified Fabric API**.
   It is meant to be a direct *replacement* for the Fabric API and is not compatible with it.
   We'll try our best to avoid loading the Fabric API if it's installed automatically (e.g.
   by your modpack manager), but if you have the option to avoid installing it, please do so.  
[\[CurseForge\]](https://legacy.curseforge.com/minecraft/mc-mods/forgified-fabric-api) [\[Modrinth\]](https://modrinth.com/mod/forgified-fabric-api) [\[GitHub\]](https://github.com/Sinytra/ForgifiedFabricAPI/releases/latest)
5. You're good to go! With all dependencies installed, grab your favourite Fabric mods and
   **just drop them in the mods folder** like you would with any Forge mods. Connector will handle loading them for you
   with no additional steps required.

## 🔍 Get help

If you're having trouble running a mod on Connector, join our community on [Discord](https://discord.gg/mamk7z3TKZ), ask us
on [GitHub Discussions](https://github.com/Sinytra/Connector/discussions) or open an issue in this repository.

Here's a few tips to follow when reporting issues:

1. Make sure you are using latest available version. Look for existing issues that might've already been answered /
   fixed. Think about whether the issue is caused by Connector itself and not another mod you've installed. To test
   this, try reproducing the same issue on Fabric.
2. Navigate to [the issues tab](https://github.com/Sinytra/Connector/issues) and open
   a [new issue](https://github.com/Sinytra/Connector/issues/new/choose). Select one of the available templates
   depending on the topic. Fill in the required fields. In order to increase our chances of identifying and reproducing
   the issue, please make sure to include as many details as possible.
3. Once you're done describing the problem, submit the issue. We'll get to you as soon as we can.

Please note that providing as many details as possible is crucial to help us find and resolve the issue faster, while
also getting you a fixed version ASAP.

## ⚖️ License

Sinytra Connector is, and will always remain, licensed under the [MIT License](https://github.com/Sinytra/Connector/blob/master/LICENSE). All files in this repository should be
treated as such unless otherwise explicitly stated.

## 🤝 Contributing

Before you decide to make major changes, you might want to discuss them with us beforehand, so that you're not wasting
your time.
To submit your changes to the project, you can contribute
via [Pull-Request](https://help.github.com/articles/creating-a-pull-request).

Here's a few tips to help get your PR approved:

* A PR should be focused on content, rather than syntax changes.
* Use the file you are editing as a style guide.
* Make sure your feature isn't already in the works, or hasn't been rejected previously.

## 🛠️ Developer guide

If you're a mod developer and you'd like to run Connector in your dev environment, it is possible in just a few steps.

1. You need to have [MixinGradle](https://github.com/SpongePowered/MixinGradle) installed for mixins to be remapped
   properly.
2. Fabric mods must be in `intermediary` mappings at runtime.

#### Gradle Usage

```groovy
repositories {
    maven {
        name = "Sinytra"
        url = "https://maven.su5ed.dev/releases"
    }
}
dependencies {
    // Add Connector to the launch classpath
    minecraftLibrary fg.deobf("dev.su5ed.sinytra:Connector:<version>")
    // Add FFAPI dependency (if required)
    runtimeOnly fg.deobf("dev.su5ed.sinytra.fabric-api:fabric-api:<version>")
    // Install desired Fabric mods 
    implementation "some.fabric:mod:<version>"
}
// Attach clean minecraft artifact path to runs, necessary for Connector to work
afterEvaluate {
    def cleanArtifactJar = Objects.requireNonNull(net.minecraftforge.gradle.common.util.MavenArtifactDownloader.generate(project, "net.minecraft:joined:${project.MCP_VERSION}:srg", true), "Cannot find clean minecraft artifact")
    minecraft.runs.configureEach {
        property("connector.clean.path", cleanArtifactJar)
    }
}
```

## ⚙️ Configuration

All information regarding Connector's configuration options can be found [here](https://sinytra.org/docs).