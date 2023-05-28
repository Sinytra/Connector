package dev.su5ed.connector.remapper

import groovy.lang.Closure
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import net.minecraftforge.gradle.common.util.BaseRepo
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

open class YarnDeobfExtension(private val project: Project, private val remapper: YarnDependencyRemapper, deobfuscatingRepo: YarnDeobfuscatingRepo) {
    val repository: GradleRepositoryAdapter = BaseRepo.Builder()
        .add(deobfuscatingRepo)
        .attach(project, "bundled_yarn_deobf_repo")

    fun deobf(dependency: Any): Dependency = deobf(dependency, null)

    fun deobf(dependency: Any, configure: Closure<*>?): Dependency {
        val baseDependency = project.dependencies.create(dependency, configure)
        project.configurations.getByName(YarnMapperPlugin.YARN_OBF).dependencies.add(baseDependency)

        return remapper.remap(baseDependency)
    }
}