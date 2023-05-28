package dev.su5ed.connector.remapper

import net.minecraftforge.gradle.common.util.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class YarnMapperPlugin : Plugin<Project> {
    companion object {
        val EXTENSION_NAME = "yarnDeobf"
        val YARN_OBF = "__yarn_obfuscated"
    }

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("net.minecraftforge.gradle") {
            project.logger.info("Appyling Yarn Mapper after FG ${it.id}")

            // Let gradle handle the downloading by giving it a configuration to dl. We'll focus on applying mappings to it.
            val internalObfConfiguration: Configuration = project.configurations.create(YARN_OBF)
            internalObfConfiguration.setDescription("Generated scope for obfuscated dependencies")

            // Create extension for dependency remapping
            // Can't create at top-level or put in `minecraft` ext due to configuration name conflict
            val deobfuscator = YarnDeobfuscator(project, Utils.getCache(project, "deobf_yarn_dependencies"))
            val remapper = YarnDependencyRemapper(project)
            project.extensions.create(EXTENSION_NAME, YarnDeobfExtension::class.java, project, remapper, YarnDeobfuscatingRepo(project, internalObfConfiguration, deobfuscator))

            project.afterEvaluate {
                remapper.attachMappings("yarn-1.19.4")
            }
        }
    }
}