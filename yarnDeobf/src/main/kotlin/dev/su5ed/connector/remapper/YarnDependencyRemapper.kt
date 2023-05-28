package dev.su5ed.connector.remapper

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import java.util.function.Consumer

class YarnDependencyRemapper(private val project: Project) {
    private val mappingListeners: MutableList<Consumer<String>> = ArrayList()

    fun remap(dependency: Dependency): Dependency {
        if (dependency is ExternalModuleDependency) {
            return remapExternalModule(dependency)
        }
        if (dependency is FileCollectionDependency) {
            project.logger.warn("files(...) dependencies are not deobfuscated. Use a flatDir repository instead: https://docs.gradle.org/current/userguide/declaring_repositories.html#sub:flat_dir_resolver")
        }
        project.logger.warn("Cannot deobfuscate dependency of type {}, using obfuscated version!", dependency.javaClass.simpleName)
        return dependency
    }

    private fun remapExternalModule(dependency: ExternalModuleDependency): ExternalModuleDependency {
        val newDep = dependency.copy()
        mappingListeners.add(Consumer { m: String -> newDep.version { v: MutableVersionConstraint -> v.strictly(newDep.version + "_ymapped_" + m) } })
        return newDep
    }

    fun attachMappings(mappings: String) {
        mappingListeners.forEach(Consumer { l: Consumer<String> -> l.accept(mappings) })
    }
}
