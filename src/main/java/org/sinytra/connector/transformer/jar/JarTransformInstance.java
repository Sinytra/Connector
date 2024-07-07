package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.internal.EnhancedRemapper;
import net.minecraftforge.srgutils.IMappingFile;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.neoforgespi.locating.IModFile;
import org.sinytra.adapter.patch.LVTOffsets;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.serialization.PatchSerialization;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.adapter.patch.util.provider.ZipClassLookup;
import org.sinytra.connector.locator.EmbeddedDependencies;
import org.sinytra.connector.service.FabricMixinBootstrap;
import org.sinytra.connector.transformer.AccessWidenerTransformer;
import org.sinytra.connector.transformer.AccessorRedirectTransformer;
import org.sinytra.connector.transformer.FieldToMethodTransformer;
import org.sinytra.connector.transformer.JarSignatureStripper;
import org.sinytra.connector.transformer.MappingAwareReferenceMapper;
import org.sinytra.connector.transformer.MixinPatchTransformer;
import org.sinytra.connector.transformer.ModMetadataGenerator;
import org.sinytra.connector.transformer.OptimizedRenamingTransformer;
import org.sinytra.connector.transformer.RefmapRemapper;
import org.sinytra.connector.transformer.patch.ClassAnalysingTransformer;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.sinytra.connector.transformer.patch.ConnectorRefmapHolder;
import org.sinytra.connector.transformer.patch.ReflectionRenamingTransformer;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class JarTransformInstance {
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MappingAwareReferenceMapper remapper;
    private final List<? extends Patch> adapterPatches;
    private final LVTOffsets lvtOffsetsData;
    private final BytecodeFixerUpperFrontend bfu;
    private final EnhancedRemapper enhancedRemapper;
    private final ClassLookup cleanClassLookup;
    private final List<Path> libs;

    public JarTransformInstance(ClassProvider classProvider, Collection<IModFile> loadedMods, List<Path> libs) {
        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        resolver.getMap(JarTransformer.OBF_NAMESPACE, JarTransformer.SOURCE_NAMESPACE);
        resolver.getMap(JarTransformer.SOURCE_NAMESPACE, JarTransformer.OBF_NAMESPACE);
        this.remapper = new MappingAwareReferenceMapper(resolver.getCurrentMap(JarTransformer.SOURCE_NAMESPACE));

        Path patchDataPath = EmbeddedDependencies.getAdapterData(EmbeddedDependencies.ADAPTER_PATCH_DATA);
        try (Reader reader = Files.newBufferedReader(patchDataPath)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
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
        IMappingFile mappingFile = FabricLoaderImpl.INSTANCE.getMappingResolver().getCurrentMap(JarTransformer.SOURCE_NAMESPACE);
        ClassProvider intermediaryClassProvider = new OptimizedRenamingTransformer.IntermediaryClassProvider(classProvider, mappingFile, mappingFile.reverse(), s -> {});
        this.enhancedRemapper = new OptimizedRenamingTransformer.MixinAwareEnhancedRemapper(intermediaryClassProvider, mappingFile, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE), s -> {});
        this.cleanClassLookup = createCleanClassLookup();
        this.libs = libs;

        MixinPatchTransformer.completeSetup(loadedMods);
    }

    public BytecodeFixerUpperFrontend getBfu() {
        return bfu;
    }

    public void transformJar(File input, Path output, JarTransformer.FabricModFileMetadata metadata) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        if (metadata.generated()) {
            processGeneratedJar(input, output, metadata, stopwatch);
            return;
        }

        String jarMapping = metadata.manifestAttributes().getValue(FABRIC_MAPPING_NAMESPACE);
        if (jarMapping != null && !jarMapping.equals(JarTransformer.SOURCE_NAMESPACE)) {
            LOGGER.error("Found transformable jar with unsupported mapping {}, currently only {} is supported", jarMapping, JarTransformer.SOURCE_NAMESPACE);
        }

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        RefmapRemapper.RefmapFiles refmap = RefmapRemapper.processRefmaps(input.toPath(), metadata.refmaps(), this.remapper, this.libs);
        IMappingFile srgToIntermediary = resolver.getMap(JarTransformer.OBF_NAMESPACE, JarTransformer.SOURCE_NAMESPACE);
        IMappingFile intermediaryToSrg = resolver.getCurrentMap(JarTransformer.SOURCE_NAMESPACE);
        AccessorRedirectTransformer accessorRedirectTransformer = new AccessorRedirectTransformer(srgToIntermediary);

        List<Patch> extraPatches = Stream.concat(this.adapterPatches.stream(), AccessorRedirectTransformer.PATCHES.stream()).toList();
        ConnectorRefmapHolder refmapHolder = new ConnectorRefmapHolder(refmap.merged(), refmap.files());
        int fabricLVTCompatibility = FabricMixinBootstrap.MixinConfigDecorator.getMixinCompat(metadata.modMetadata());
        PatchEnvironment environment = PatchEnvironment.create(refmapHolder, this.cleanClassLookup, this.bfu.unwrap(), fabricLVTCompatibility);
        MixinPatchTransformer patchTransformer = new MixinPatchTransformer(this.lvtOffsetsData, environment, extraPatches);
        RefmapRemapper refmapRemapper = new RefmapRemapper(metadata.visibleMixinConfigs(), refmap.files());
        Renamer.Builder builder = Renamer.builder()
            .add(new JarSignatureStripper())
            .add(new ClassNodeTransformer(
                new FieldToMethodTransformer(metadata.modMetadata().getAccessWidener(), srgToIntermediary),
                accessorRedirectTransformer,
                new ReflectionRenamingTransformer(intermediaryToSrg, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE))
            ))
            .add(new OptimizedRenamingTransformer(this.enhancedRemapper, false, metadata.refmaps().isEmpty()))
            .add(new ClassNodeTransformer(new ClassAnalysingTransformer()))
            .add(patchTransformer)
            .add(refmapRemapper)
            .add(new ModMetadataGenerator(metadata.modMetadata().getId()))
            .logger(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(JarTransformer.TRANSFORM_MARKER, s))
            .ignoreJarPathPrefix("assets/", "data/");
        if (!metadata.containsAT()) {
            builder.add(new AccessWidenerTransformer(metadata.modMetadata().getAccessWidener(), resolver, IntermediateMapping.get(JarTransformer.SOURCE_NAMESPACE)));
        }
        try (Renamer renamer = builder.build()) {
            accessorRedirectTransformer.analyze(input, metadata.mixinPackages(), environment);

            renamer.run(input, output.toFile());

            try (FileSystem zipFile = FileSystems.newFileSystem(output)) {
                patchTransformer.finalize(zipFile.getPath("/"), metadata.mixinConfigs(), refmap.files(), refmapHolder.getDirtyRefmaps());
            }
        } catch (Throwable t) {
            LOGGER.error("Encountered error while transforming jar file " + input.getAbsolutePath(), t);
            throw t;
        }

        stopwatch.stop();
        LOGGER.debug(JarTransformer.TRANSFORM_MARKER, "Jar {} transformed in {} ms", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static void processGeneratedJar(File input, Path output, JarTransformer.FabricModFileMetadata metadata, Stopwatch stopwatch) throws IOException {
        Files.copy(input.toPath(), output);
        stopwatch.stop();
        LOGGER.debug(JarTransformer.TRANSFORM_MARKER, "Skipping transformation of jar {} after {} ms as it contains generated metadata, assuming it's a java library", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static ClassLookup createCleanClassLookup() {
        String mcAndNeoFormVersion = FMLLoader.versionInfo().mcAndNeoFormVersion();
        if (FMLEnvironment.production) {
            MavenCoordinate coords = new MavenCoordinate("net.minecraft", "client", "", "srg", mcAndNeoFormVersion);
            Path path = LibraryFinder.findPathForMaven(coords);
            if (!Files.exists(path)) {
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(path));
            }
            ZipFile zipFile = uncheck(() -> new ZipFile(path.toFile()));
            return new ZipClassLookup(zipFile);
        }
        else {
            // Search for system property
            Path cleanPath = Optional.ofNullable(System.getProperty("connector.clean.path"))
                .map(Path::of)
                .orElseThrow(() -> new RuntimeException("Could not determine clean minecraft artifact path"));
            return new SimpleClassLookup(ClassProvider.fromPaths(cleanPath));
        }
    }
}
