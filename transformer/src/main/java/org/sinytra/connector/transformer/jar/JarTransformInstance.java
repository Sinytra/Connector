package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.Transformer;
import net.neoforged.art.api.Transformer.ResourceEntry;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.adapter.util.provider.MixinClassLookup;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.api.TransformerContext;
import org.sinytra.connector.transformer.api.TransformerIds;
import org.sinytra.connector.transformer.api.TransformerRegistrar.OrderingHint;
import org.sinytra.connector.transformer.patch.ConnectorRefmapHolder;
import org.sinytra.connector.transformer.patch.RefmapStorage;
import org.sinytra.connector.transformer.patch.RefmapStorage.RefmapFiles;
import org.sinytra.connector.transformer.plugin.PluginManager;
import org.sinytra.connector.transformer.plugin.TransformerContextImpl;
import org.sinytra.connector.transformer.transform.FabricMetadataTransformer;
import org.sinytra.connector.transformer.transform.MixinPatchTransformer;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    public JarTransformInstance(TransformerEnvironment environment) {
        this.environment = environment;

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
            processGeneratedJar(input, output, stopwatch);
            return null;
        }

        RefmapFiles refmap = RefmapStorage.processRefmaps(input.toPath(), metadata.refmaps());
        AuditTrail jarTrail = AuditTrail.create();
        ConnectorRefmapHolder refmapHolder = new ConnectorRefmapHolder(refmap.merged(), refmap.files());
        int fabricLVTCompatibility = this.environment.getFabricMixinCompatibility(metadata.modMetadata());
        PatchEnvironment environment = PatchEnvironment.create(refmapHolder, this.cleanClassLookup, this.bfu.unwrap(), fabricLVTCompatibility, jarTrail);

        PluginManager plugins = PluginManager.getInstance();
        TransformerContext context = new TransformerContextImpl(metadata, environment, this.environment);
        List<MethodPatch> patches = plugins.gatherMethodPatches(context);

        MixinPatchTransformer patchTransformer = new MixinPatchTransformer(this.environment, environment, patches);
        List<Transformer> transformers = plugins.gatherJarTranformers(context, r ->
            r.register(TransformerIds.METHOD_PATCHES, null, null, OrderingHint.LATE, patchTransformer));

        Renamer.Builder builder = Renamer.builder()
            .logger(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .ignoreJarPathPrefix("assets/", "data/");
        transformers.forEach(builder::add);

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

    private void processGeneratedJar(File input, Path output, Stopwatch stopwatch) throws IOException {
        Files.copy(input.toPath(), output);
        FabricMetadataTransformer transformer = new FabricMetadataTransformer(this.environment);

        try (FileSystem fs = FileSystems.newFileSystem(output)) {
            Path path = fs.getPath(TransformerUtil.FABRIC_MOD_JSON);
            byte[] data = Files.readAllBytes(path);
            ResourceEntry entry = ResourceEntry.create(TransformerUtil.FABRIC_MOD_JSON, 0, data);
            ResourceEntry processed = Objects.requireNonNull(transformer.process(entry), "Failed to process FMJ entry");
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
