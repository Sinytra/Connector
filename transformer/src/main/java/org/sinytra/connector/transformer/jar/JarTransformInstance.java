package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.Transformer.ResourceEntry;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.adapter.util.provider.MixinClassLookup;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.patch.ClassAnalysingTransformer;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.sinytra.connector.transformer.patch.ConnectorRefmapHolder;
import org.sinytra.connector.transformer.patch.RefmapStorage;
import org.sinytra.connector.transformer.patch.RefmapStorage.RefmapFiles;
import org.sinytra.connector.transformer.transform.*;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JarTransformInstance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BytecodeFixerUpperFrontend bfu;
    private final ClassLookup cleanClassLookup;
    private final AuditTrail auditTrail;
    private final TransformerEnvironment environment;
    private final FabricMetadataTransformer metadataTransformer;

    public JarTransformInstance(TransformerEnvironment environment) {
        this.environment = environment;
        this.metadataTransformer = new FabricMetadataTransformer(environment.getModIdAliases());

        this.cleanClassLookup = environment.getCleanClassLookup();
        this.bfu = new BytecodeFixerUpperFrontend(this.cleanClassLookup, MixinClassLookup.INSTANCE, this.environment);
        this.auditTrail = AuditTrail.create();
    }

    public BytecodeFixerUpperFrontend getBfu() {
        return bfu;
    }

    @Nullable
    public AuditTrail transformJar(File input, Path output, FabricModFileMetadata metadata) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        if (metadata.generated()) {
            processGeneratedJar(input, output, stopwatch, this.metadataTransformer);
            return null;
        }

        RefmapFiles refmap = RefmapStorage.processRefmaps(input.toPath(), metadata.refmaps());
        AccessorRedirectTransformer accessorRedirectTransformer = new AccessorRedirectTransformer();

        AuditTrail jarTrail = AuditTrail.create();
        ConnectorRefmapHolder refmapHolder = new ConnectorRefmapHolder(refmap.merged(), refmap.files());
        int fabricLVTCompatibility = this.environment.getFabricMixinCompatibility(metadata.modMetadata());
        PatchEnvironment environment = PatchEnvironment.create(refmapHolder, this.cleanClassLookup, this.bfu.unwrap(), fabricLVTCompatibility, jarTrail);
        MixinPatchTransformer patchTransformer = new MixinPatchTransformer(this.environment, environment, accessorRedirectTransformer.getPatches());
        FabricEnumExtensionTransformer enumExtensionTransformer = new FabricEnumExtensionTransformer(metadata);

        Renamer.Builder builder = Renamer.builder()
            .add(new JarSignatureStripper())
            .add(this.metadataTransformer)
            .add(enumExtensionTransformer)
            .add(new ClassNodeTransformer(
                new FieldToMethodTransformer(metadata.modMetadata().getClassTweaker()),
                new ClassAnalysingTransformer()
            ))
            .add(patchTransformer)
            .add(new ClassNodeTransformer(accessorRedirectTransformer))
            .logger(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .ignoreJarPathPrefix("assets/", "data/");

        try (Renamer renamer = builder.build()) {
            renamer.run(input, output.toFile());

            try (FileSystem zipFile = FileSystems.newFileSystem(output)) {
                enumExtensionTransformer.writeGeneratedResources(zipFile.getPath("/"));
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

    private static void processGeneratedJar(File input, Path output, Stopwatch stopwatch, FabricMetadataTransformer metadataTransformer) throws IOException {
        Files.copy(input.toPath(), output);

        try (FileSystem fs = FileSystems.newFileSystem(output)) {
            Path path = fs.getPath(TransformerUtil.FABRIC_MOD_JSON);
            byte[] data = Files.readAllBytes(path);
            ResourceEntry entry = ResourceEntry.create(TransformerUtil.FABRIC_MOD_JSON, 0, data);
            ResourceEntry processed = Objects.requireNonNull(metadataTransformer.process(entry), "Failed to process FMJ entry");
            Files.write(path, processed.getData());
        } catch (IOException e) {
            throw new UncheckedIOException("Error patching generated jar file", e);
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
