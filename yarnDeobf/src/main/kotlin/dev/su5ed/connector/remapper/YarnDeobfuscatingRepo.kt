package dev.su5ed.connector.remapper

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.gradle.common.util.Artifact
import net.minecraftforge.gradle.common.util.BaseRepo
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.Utils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import java.io.File
import java.io.IOException
import java.util.*

class YarnDeobfuscatingRepo(private val project: Project, private val origin: Configuration, private val deobfuscator: YarnDeobfuscator) : BaseRepo(Utils.getCache(project, "mod_yarn_remap_repo"), project.logger) {
    private var resolvedOrigin: ResolvedConfiguration? = null

    private fun getMappings(version: String): String? {
        return if (!version.contains("_ymapped_")) null else version.split("_ymapped_").last()
    }

    @Throws(IOException::class)
    override fun findFile(artifact: ArtifactIdentifier): File? {
        var version = artifact.version
        val mappings = getMappings(version) ?: return null
        //We only care about the remapped files, not orig
        version = version.substring(0, version.length - (mappings.length + "_ymapped_".length))
        val classifier = if (artifact.classifier == null) "" else artifact.classifier
        val unmappedArtifact = Artifact.from(artifact).withVersion(version)
        val ext = unmappedArtifact.extension
        debug("  " + REPO_NAME + " Request: " + clean(artifact) + " Mapping: " + mappings)
        return if ("pom" == ext) {
            findPom(unmappedArtifact, mappings)
        } else if ("jar" == ext) {
            if ("sources" == classifier) {
                findSource(unmappedArtifact, mappings)
            } else findRaw(unmappedArtifact, mappings)
        } else {
            throw RuntimeException("Invalid deobf dependency: $artifact")
        }
    }

    override fun configureFilter(filter: RepositoryContentDescriptor) {
        filter.includeVersionByRegex(".*", ".*", ".*_ymapped_.*") // Any group, any module BUT version must contain _ymapped_
    }

    @Throws(IOException::class)
    private fun findPom(artifact: Artifact, mapping: String): File? {
        val orig = findArtifactFile(artifact)
        if (!orig.isPresent) {
            return null
        }
        val origFile = orig.get()
        return deobfuscator.deobfPom(origFile, mapping, getArtifactPath(artifact, mapping))
    }

    fun getResolvedOrigin(): ResolvedConfiguration {
        synchronized(origin) {
            if (resolvedOrigin == null) {
                resolvedOrigin = origin.resolvedConfiguration
            }
            return resolvedOrigin!!
        }
    }

    private fun findArtifactFile(artifact: Artifact): Optional<File> {
        val deps = getResolvedOrigin().getFirstLevelModuleDependencies(artifact.asDependencySpec()).stream()
        return deps.flatMap { d: ResolvedDependency ->
            d.moduleArtifacts.stream()
                .filter(artifact.asArtifactMatcher())
        }.map { obj: ResolvedArtifact -> obj.file }.filter { obj: File -> obj.exists() }.findAny()
    }

    @Throws(IOException::class)
    private fun findRaw(artifact: Artifact, mapping: String): File? {
        val orig = findArtifactFile(artifact)
        if (!orig.isPresent) {
            return null
        }
        val origFile = orig.get()
        return deobfuscator.deobfBinary(origFile, mapping, getArtifactPath(artifact, mapping))
    }

    @Throws(IOException::class)
    private fun findSource(artifact: Artifact, mapping: String): File? {
        val origFile = MavenArtifactDownloader.manual(project, artifact.descriptor, false) ?: return null
        return deobfuscator.deobfSources(origFile, mapping, getArtifactPath(artifact, mapping))
    }

    private fun getArtifactPath(artifact: Artifact, mappings: String): String {
        val newVersion = artifact.version + "_ymapped_" + mappings
        return artifact.withVersion(newVersion).localPath
    }
}