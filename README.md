# Sinytra Connector

[![Build](https://github.com/Sinytra/Connector/actions/workflows/build.yml/badge.svg)](https://github.com/Sinytra/Connector/actions/workflows/build.yml)
[![GitHub Releases](https://img.shields.io/github/v/release/Sinytra/Connector?style=flat&label=Release)](https://github.com/Sinytra/Connector/releases/latest)
[![CurseForge](https://cf.way2muchnoise.eu/title/sinytra-connector.svg)](https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector)
[![Modrinth](https://img.shields.io/modrinth/dt/u58R1TMW?color=00AF5C&label=modrinth&style=flat&logo=modrinth)](https://modrinth.com/mod/connector)
[![ForgifiedFabricAPI](https://raw.githubusercontent.com/Sinytra/.github/main/badges/forgified-fabric-api/compacter.svg)](https://github.com/Sinytra/ForgifiedFabricAPI)

## About

Sinytra Connector is a translation/compatibility layer that allows running [Fabric](https://fabricmc.net) mods on [MinecraftForge](https://minecraftforge.net). Its goal is to bring the two platforms closer together, saving developers time and effort maintaining their mods for multiple platforms at once, as well as allowing players to play all their favourite mods in one modpack.

Also check out [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI), a port of Fabric API to MinecraftForge.  
To learn more about how Connector works, you can read our [Introductory blog post](https://github.com/Sinytra/Connector/discussions/11).

## Usage Guide

### Players

To install Connector and its dependencies, follow the same installation steps as you would for any other mods:
1. If you haven't already, install MinecraftForge. We recommend using the latest available version.
2. Get the latest release from one of our official distribution channels and drop the jar in your mods folder.
   - [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector)
   - [Modrinth](https://modrinth.com/mod/connector)
   - [GitHub](https://github.com/Sinytra/Connector/releases)
3. Following that, you'll want to download the [**Forgified Fabric API**](https://github.com/Sinytra/ForgifiedFabricAPI). It is meant to be a direct *replacement* for the Fabric API and is not compatible with it. We'll try our best to avoid loading the Fabric API if it's installed automatically (e.g. by your modpack manager), but if you have the option to do so, don't install it. The Forgified Fabric API is available fo download on the following platforms:
   - [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/forgified-fabric-api)
   - [Modrinth](https://modrinth.com/mod/forgified-fabric-api)
   - [GitHub](https://github.com/Sinytra/ForgifiedFabricAPI/releases/latest)
4. Now that you have all dependencies installed, grab your favourite Fabric mods and **just drop them in the mods folder** like you would with any Forge mods. Connector will handle loading them for you with no additional steps required.

### Developers

For when you want to run Fabric mods in your Forge dev environment.
Connector can currently run in developer environments with a few additional requirements:
1. You need to have [MixinGradle](https://github.com/SpongePowered/MixinGradle) installed for mixins to be remapped properly.
2. Fabric mods must be in `intermediary` mappings.

#### Gradle Usage

```groovy
repositories {
    maven {
        name = "Sinytra"
        url = "https://maven.su5ed.dev/releases"
    }
}
dependencies {
    implementation fg.deobf("dev.su5ed.sinytra:Connector:<version>")
}
```

## Get help

If you're having trouble running a mod on Connector, ask us on [GitHub Discussions](https://github.com/Sinytra/Connector/discussions) or open an issue in this repository.

Here's a few tips to follow when reporting issues:
1. Make sure you are using latest available version. Look for existing issues that might've already been answered / fixed. Think about whether the issue is caused by Connector itself and not another mod you've installed. To test this, try reproducing the same issue on Fabric.
2. Navigate to [the issues tab](https://github.com/Sinytra/Connector/issues) and open a [new issue](https://github.com/Sinytra/Connector/issues/new/choose). Select one of the available templates depending on the topic. Fill in the required fields. In order to increase our chances of identifying and reproducing the issue, please make sure to include as many details as possible.
3. Once you're done describing the problem, submit the issue. We'll get to you as soon as we can.

Please note that providing as many details as possible is crucial to help us find and resolve the issue faster, while also getting you a fixed version ASAP.

## Downloads

Connector is distributed on the following platforms:
- [GitHub Releases](https://github.com/Sinytra/Connector/releases/latest)
- [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector)
- [Modrinth](https://modrinth.com/mod/connector)

## License

Sinytra Connector is, and will always remain, licensed under the MIT license. All files in this repository should be treated as such unless otherwise explicitly stated.

## Contributing

Before you decide to make major changes, you might want to discuss them with us beforehand, so that you're not wasting your time.
To submit your changes to the project, you can contribute via [Pull-Request](https://help.github.com/articles/creating-a-pull-request).

<!-- TODO The [guidelines for contributing](INSERT LINK HERE) contain more detailed information about topics like the used code style and should also be considered. -->

Here's a few tips to help get your PR approved:
* A PR should be focused on content, rather than syntax changes.
* Use the file you are editing as a style guide.
* Make sure your feature isn't already in the works, or hasn't been rejected previously.
