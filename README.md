<p align="center">
  <a href="https://connector.sinytra.org">
    <img height="163px" alt="banner" src="https://raw.githubusercontent.com/Sinytra/.github/main/art/connector_banner.svg">
  </a>
</p>
<p align="center">
  <a href="https://github.com/Sinytra/Connector/actions/workflows/build.yml"><img src="https://github.com/Sinytra/Connector/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/Sinytra/Connector/releases/latest"><img src="https://img.shields.io/github/v/release/Sinytra/Connector?style=flat&label=Release&include_prereleases&sort=semver"></a>
  <a href="https://legacy.curseforge.com/minecraft/mc-mods/sinytra-connector"><img src="https://cf.way2muchnoise.eu/short_sinytra-connector.svg"></a>
  <a href="https://modrinth.com/mod/connector"><img src="https://img.shields.io/modrinth/dt/u58R1TMW?color=00AF5C&label=modrinth&style=flat&logo=modrinth"></a>
  <a href="https://discord.sinytra.org"><img src="https://img.shields.io/discord/1141048834177388746?logo=discord&logoColor=white&label=Discord&color=5865f2"></a>
  <a href="https://nightly.link/Sinytra/Connector/workflows/build/dev/Nightly%20mod%20jar.zip"><img src="https://img.shields.io/badge/Nightly-Download-9a32f0?logo=github"></a>
</p>

## 📖 About

**Sinytra Connector** is a translation/compatibility layer that allows running [Fabric](https://fabricmc.net) mods
on [NeoForge](https://neoforged.net). Its goal is to bring the two platforms closer together, saving
developers time and effort maintaining their mods for multiple platforms at once, as well as allowing players to play
all their favourite mods in one modpack.

**📘 Find compatible mods & official docs at [connector.sinytra.org](https://connector.sinytra.org).**

### 🔗 Related Projects

- Visit our [website](https://connector.sinytra.org) to find information on mod compatibility and common issues
- Developing cross-platform mods? Check out [Launchpad](https://github.com/Sinytra/Launchpad) and [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI)
- Install [Connector Extras](https://github.com/Sinytra/ConnectorExtras) for improved compatibility with third-party libraries and APIs

### 💬 Join the Community

We have an official [Discord community](https://discord.sinytra.org) for Connector. By joining, you can:

- Get help and technical support from our team and community members
- Keep in touch with the latest development updates and community events
- Engage in the project's development and collaborate with our team

## 📋 Usage Guide

To install Connector and its dependencies, follow the standard installation steps:

1. Install [**NeoForge**](https://neoforged.net). We recommend using the latest stable version.

2. Install Connector and dependencies
   - [**Forgified Fabric API**](https://github.com/Sinytra/ForgifiedFabricAPI/releases/latest)
   - [**Launchpad**](https://github.com/Sinytra/Launchpad/releases/latest)
   - [**Connector**](https://github.com/Sinytra/Connector/releases/latest)

3. You're good to go! With all dependencies installed, grab your favourite Fabric mods and
   **just drop them in the mods folder** like you would with any NeoForge mods. Connector will handle loading them for
   you with no additional steps required.

## 🔍 Get help

If you're having trouble running a mod on Connector, join our community on [Discord](https://discord.sinytra.org), ask us
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

### Supported versions

✅ **26.1.2** is our **primary supported version** and will receive the latest fixes and compatibility improvements.

⚠️ **1.21.1** is our **long-term-support** version and will still receive critical bugfixes.
The latest compatibility fixes might not be available.

## ⚖️ License

Sinytra Connector is, and will always remain, licensed under the [MIT License](https://github.com/Sinytra/Connector/blob/master/LICENSE).
All files in this repository should be treated as such unless otherwise explicitly stated.

## 🤝 Contributing

Before you decide to make major changes, you might want to discuss them with us beforehand, so that you're not wasting
your time. To submit your changes to the project, you can contribute
via [Pull-Request](https://help.github.com/articles/creating-a-pull-request).

Here's a few tips to help get your PR approved:

- A PR should be focused on content, rather than syntax changes.
- Use the file you are editing as a style guide.
- Make sure your feature isn't already in the works, or hasn't been rejected previously.

## 🛠️ Developer guide

If you're a mod developer and you'd like to run Connector in your dev environment, it is possible in just a few steps.

#### ModDevGradle Usage

```groovy
repositories {
    maven {
        name = "Sinytra"
        url = "https://maven.sinytra.org/"
    }
}
dependencies {
    // Add dependencies
    implementation "org.sinytra.launchpad:launchpad:<version>"
    implementation "org.sinytra.forgified-fabric-api:forgified-fabric-api:<version>"
    // Add connector
    implementation "org.sinytra:connector:<version>"

    // Install desired Fabric mods
    implementation "some.fabric:mod:<version>"
}
```

### Transformer Plugins

Developers can create addons that register their own jar transformers and Mixin method patches in addition to those
provided by Connector.

See the [Plugins](https://moddedmc.wiki/en/project/connector/latest/docs/plugins) documentation page for a guide. Code
documentation is available in the `org.sinytra.connector.transformer.api` package.

## ⚙️ Configuration

All information regarding Connector's configuration options can be found on
[our website](https://moddedmc.wiki/project/connector).
