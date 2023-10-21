package dev.su5ed.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.su5ed.sinytra.adapter.patch.LVTOffsets;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.serialization.PatchSerialization;
import dev.su5ed.sinytra.connector.locator.EmbeddedDependencies;
import dev.su5ed.sinytra.connector.transformer.AccessWidenerTransformer;
import dev.su5ed.sinytra.connector.transformer.AccessorRedirectTransformer;
import dev.su5ed.sinytra.connector.transformer.FieldToMethodTransformer;
import dev.su5ed.sinytra.connector.transformer.MixinPatchTransformer;
import dev.su5ed.sinytra.connector.transformer.ModMetadataGenerator;
import dev.su5ed.sinytra.connector.transformer.OptimizedRenamingTransformer;
import dev.su5ed.sinytra.connector.transformer.RefmapRemapper;
import dev.su5ed.sinytra.connector.transformer.SrgRemappingReferenceMapper;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static dev.su5ed.sinytra.connector.transformer.jar.JarTransformer.*;

public class JarTransformInstance {
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SrgRemappingReferenceMapper remapper;
    private final List<? extends Patch> adapterPatches;
    private final LVTOffsets lvtOffsetsData;
    private final BytecodeFixerUpperFrontend bfu;
    private final Transformer remappingTransformer;

    public JarTransformInstance(ClassProvider classProvider) {
        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        resolver.getMap(OBF_NAMESPACE, SOURCE_NAMESPACE);
        resolver.getMap(SOURCE_NAMESPACE, OBF_NAMESPACE);
        this.remapper = new SrgRemappingReferenceMapper(resolver.getCurrentMap(SOURCE_NAMESPACE));

        Path patchDataPath = EmbeddedDependencies.getAdapterData(EmbeddedDependencies.ADAPTER_PATCH_DATA);
        try (Reader reader = Files.newBufferedReader(patchDataPath)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            PatchEnvironment.setReferenceMapper(str -> str.startsWith("m_") ? ASMAPI.mapMethod(str) : ASMAPI.mapField(str));
            this.adapterPatches = PatchSerialization.deserialize(json, JsonOps.INSTANCE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Path offsetDataPath = EmbeddedDependencies.getAdapterData(EmbeddedDependencies.ADAPTER_LVT_OFFSETS);
        try (Reader reader = Files.newBufferedReader(offsetDataPath)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            this.lvtOffsetsData = LVTOffsets.fromJson(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.bfu = new BytecodeFixerUpperFrontend();

        this.remappingTransformer = OptimizedRenamingTransformer.create(classProvider, s -> {}, FabricLoaderImpl.INSTANCE.getMappingResolver().getCurrentMap(SOURCE_NAMESPACE), IntermediateMapping.get(SOURCE_NAMESPACE));
    }

    public BytecodeFixerUpperFrontend getBfu() {
        return bfu;
    }

    public void transformJar(File input, Path output, FabricModFileMetadata metadata) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        String jarMapping = metadata.manifestAttributes().getValue(FABRIC_MAPPING_NAMESPACE);
        if (jarMapping != null && !jarMapping.equals(SOURCE_NAMESPACE)) {
            LOGGER.error("Found transformable jar with unsupported mapping {}, currently only {} is supported", jarMapping, SOURCE_NAMESPACE);
        }

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        RefmapRemapper.RefmapFiles refmap = RefmapRemapper.processRefmaps(input, metadata.refmaps(), this.remapper);
        IMappingFile srgToIntermediary = resolver.getMap(OBF_NAMESPACE, SOURCE_NAMESPACE);
        AccessorRedirectTransformer accessorRedirectTransformer = new AccessorRedirectTransformer(srgToIntermediary);

        List<Patch> extraPatches = Stream.concat(this.adapterPatches.stream(), AccessorRedirectTransformer.PATCHES.stream()).toList();
        PatchEnvironment environment = new PatchEnvironment(refmap.merged().mappings);
        MixinPatchTransformer patchTransformer = new MixinPatchTransformer(this.lvtOffsetsData, metadata.mixinPackages(), environment, extraPatches, this.bfu.unwrap());
        RefmapRemapper refmapRemapper = new RefmapRemapper(metadata.mixinConfigs(), refmap.files());
        Renamer.Builder builder = Renamer.builder()
            .add(new FieldToMethodTransformer(metadata.modMetadata().getAccessWidener(), srgToIntermediary))
            .add(accessorRedirectTransformer)
            .add(this.remappingTransformer)
            .add(patchTransformer)
            .add(refmapRemapper)
            .add(new ModMetadataGenerator(metadata.modMetadata().getId()))
            .logger(s -> LOGGER.trace(TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(TRANSFORM_MARKER, s));
        if (!metadata.containsAT()) {
            builder.add(new AccessWidenerTransformer(metadata.modMetadata().getAccessWidener(), resolver, IntermediateMapping.get(SOURCE_NAMESPACE)));
        }
        try (Renamer renamer = builder.build()) {
            accessorRedirectTransformer.analyze(input, metadata.mixinPackages(), environment);

            renamer.run(input, output.toFile());

            try (FileSystem zipFile = FileSystems.newFileSystem(output)) {
                patchTransformer.finalize(zipFile.getPath("/"), metadata.mixinConfigs(), refmap.merged());
            }
        } catch (Throwable t) {
            LOGGER.error("Encountered error while transforming jar file " + input.getAbsolutePath(), t);
            throw t;
        }

        stopwatch.stop();
        LOGGER.debug(TRANSFORM_MARKER, "Jar {} transformed in {} ms", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }
}
