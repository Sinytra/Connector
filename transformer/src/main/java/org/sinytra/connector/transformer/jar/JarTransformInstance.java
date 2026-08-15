package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.internal.EnhancedRemapper;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.adapter.util.provider.MixinClassLookup;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.patch.ClassAnalysingTransformer;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.sinytra.connector.transformer.patch.ConnectorRefmapHolder;
import org.sinytra.connector.transformer.patch.ReflectionRenamingTransformer;
import org.sinytra.connector.transformer.transform.*;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JarTransformInstance {
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MappingAwareReferenceMapper remapper;
    private final BytecodeFixerUpperFrontend bfu;
    private final EnhancedRemapper enhancedRemapper;
    private final ClassLookup cleanClassLookup;
    private final List<Path> libs;
    private final AuditTrail auditTrail;
    private final TransformerEnvironment environment;

    public JarTransformInstance(TransformerEnvironment environment, ClassProvider classProvider, List<Path> libs) {
        this.environment = environment;

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        resolver.getMap(JarTransformer.OBF_NAMESPACE, JarTransformer.SOURCE_NAMESPACE);
        resolver.getMap(JarTransformer.SOURCE_NAMESPACE, JarTransformer.OBF_NAMESPACE);
        this.remapper = new MappingAwareReferenceMapper(resolver.getCurrentMap(JarTransformer.SOURCE_NAMESPACE));

        IMappingFile mappingFile = FabricLoaderImpl.INSTANCE.getMappingResolver().getCurrentMap(JarTransformer.SOURCE_NAMESPACE);
        ClassProvider intermediaryClassProvider = new OptimizedRenamingTransformer.IntermediaryClassProvider(classProvider, mappingFile, mappingFile.reverse(), s -> {
        });
        this.enhancedRemapper = new OptimizedRenamingTransformer.MixinAwareEnhancedRemapper(intermediaryClassProvider, mappingFile, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE), s -> {
        });
        this.cleanClassLookup = environment.getCleanClassLookup();
        this.bfu = new BytecodeFixerUpperFrontend(this.cleanClassLookup, MixinClassLookup.INSTANCE, this.environment);
        this.libs = libs;
        this.auditTrail = AuditTrail.create();
    }

    public BytecodeFixerUpperFrontend getBfu() {
        return bfu;
    }

    @Nullable
    public AuditTrail transformJar(File input, Path output, FabricModFileMetadata metadata) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        if (metadata.generated()) {
            processGeneratedJar(input, output, stopwatch);
            return null;
        }

        String jarMapping = metadata.manifestAttributes().getValue(FABRIC_MAPPING_NAMESPACE);
        if (jarMapping != null && !jarMapping.equals(JarTransformer.SOURCE_NAMESPACE)) {
            LOGGER.error("Found transformable jar with unsupported mapping {}, currently only {} is supported", jarMapping, JarTransformer.SOURCE_NAMESPACE);
        }

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        RefmapRemapper.RefmapFiles refmap = RefmapRemapper.processRefmaps(input.toPath(), metadata.refmaps(), this.remapper, this.libs);
        IMappingFile srgToIntermediary = resolver.getMap(JarTransformer.OBF_NAMESPACE, JarTransformer.SOURCE_NAMESPACE);
        IMappingFile intermediaryToSrg = resolver.getCurrentMap(JarTransformer.SOURCE_NAMESPACE);
        AccessorRedirectTransformer accessorRedirectTransformer = new AccessorRedirectTransformer();

        AuditTrail jarTrail = AuditTrail.create();
        ConnectorRefmapHolder refmapHolder = new ConnectorRefmapHolder(refmap.merged(), refmap.files());
        int fabricLVTCompatibility = this.environment.getFabricMixinCompatibility(metadata.modMetadata());
        PatchEnvironment environment = PatchEnvironment.create(refmapHolder, this.cleanClassLookup, this.bfu.unwrap(), fabricLVTCompatibility, jarTrail);
        MixinPatchTransformer patchTransformer = new MixinPatchTransformer(this.environment, environment, accessorRedirectTransformer.getPatches());

        Renamer.Builder builder = Renamer.builder()
            .add(new JarSignatureStripper())
            .add(new ClassNodeTransformer(
                new FieldToMethodTransformer(metadata.modMetadata().getClassTweaker(), srgToIntermediary),
                new ReflectionRenamingTransformer(intermediaryToSrg, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE)),
                new ClassAnalysingTransformer()
            ))
            .add(new OptimizedRenamingTransformer(this.enhancedRemapper, false, metadata.refmaps().isEmpty()))
            .add(patchTransformer)
            .add(new ClassNodeTransformer(accessorRedirectTransformer))
            .add(new RefmapRemapper(refmap.files()))
            .logger(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .ignoreJarPathPrefix("assets/", "data/");
        if (!metadata.containsAT()) {
            builder.add(new AccessWidenerTransformer(metadata.modMetadata().getClassTweaker(), resolver, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE)));
        }

        try (Renamer renamer = builder.build()) {
            renamer.run(input, output.toFile());

            try (FileSystem zipFile = FileSystems.newFileSystem(output)) {
                patchTransformer.finalize(zipFile.getPath("/"), metadata.mixinConfigs(), refmap.files(), refmapHolder.getDirtyRefmaps());
            }
        } catch (Throwable t) {
            LOGGER.error("Encountered error while transforming jar file {}", input.getAbsolutePath(), t);
            throw t;
        }

        stopwatch.stop();
        LOGGER.debug(JarTransformer.TRANSFORM_MARKER, "Jar {} transformed in {} ms", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Silence transformation errors from mixins not present in any config file
        Set<String> ignore = jarTrail.getFailingMixins().stream()
            .map(c -> c.classNode().name)
            .filter(s -> !metadata.mixinClasses().contains(s))
            .collect(Collectors.toSet());
        jarTrail.silenceClasses(ignore);

        this.auditTrail.merge(jarTrail);
        return jarTrail;
    }

    private static void processGeneratedJar(File input, Path output, Stopwatch stopwatch) throws IOException {
        Renamer.Builder builder = Renamer.builder()
            .logger(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .ignoreJarPathPrefix("assets/", "data/");

        builder.add(new JarSignatureStripper());

        try (Renamer renamer = builder.build()) {
            renamer.run(input, output.toFile());
        } catch (Throwable t) {
            LOGGER.error("Encountered error while transforming jar file {}", input.getAbsolutePath(), t);
            throw t;
        }

        stopwatch.stop();
        LOGGER.debug(JarTransformer.TRANSFORM_MARKER, "Skipping transformation of jar {} after {} ms as it contains generated metadata, assuming it's a java library", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void saveAuditReport() {
        try {
            Path path = this.environment.getAuditReportPath();

            Files.deleteIfExists(path);
            String log = this.auditTrail.getCompleteReport();
            Files.writeString(path, log);
        } catch (IOException e) {
            LOGGER.error("Error writing patch audit report", e);
        }
    }
}
