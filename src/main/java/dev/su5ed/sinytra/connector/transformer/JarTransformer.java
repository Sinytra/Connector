package dev.su5ed.sinytra.connector.transformer;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.su5ed.sinytra.adapter.patch.MixinRemaper;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchSerialization;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.locator.EmbeddedDependencies;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public final class JarTransformer {
    private static final String MAPPED_SUFFIX = "_mapped_" + FMLEnvironment.naming + "_" + FMLLoader.versionInfo().mcVersion();
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    private static final String SOURCE_NAMESPACE = "intermediary";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker TRANSFORM_MARKER = MarkerFactory.getMarker("TRANSFORM");

    private static final Map<String, Map<String, String>> FLAT_MAPPINGS_CACHE = new HashMap<>();
    // Filter out non-obfuscated method names used in mapping namespaces as those don't need
    // to be remapped and will only cause issues with our barebones find/replace remapper
    private static final Map<String, Collection<String>> MAPPING_PREFIXES = Map.of(
        "intermediary", Set.of("net/minecraft/class_", "field_", "method_", "comp_")
    );
    private static final List<Path> RENAMER_LIBS = Stream.of(FMLLoader.getLaunchHandler().getMinecraftPaths())
        .flatMap(paths -> Stream.concat(paths.minecraftPaths().stream(), paths.otherArtifacts().stream()))
        .toList();

    private static SrgRemappingReferenceMapper remapper;
    private static List<? extends Patch> adapterPatches;

    private static Map<String, String> getFlatMapping(String sourceNamespace) {
        Map<String, String> map = FLAT_MAPPINGS_CACHE.get(sourceNamespace);
        if (map == null) {
            synchronized (JarTransformer.class) {
                Map<String, String> existing = FLAT_MAPPINGS_CACHE.get(sourceNamespace);
                if (existing != null) {
                    return existing;
                }

                LOGGER.debug(TRANSFORM_MARKER, "Creating flat mapping for namespace {}", sourceNamespace);
                // Intermediary sometimes contains duplicate names for different methods (why?). We exclude those.
                Set<String> excludedNames = new HashSet<>();
                Collection<String> prefixes = MAPPING_PREFIXES.get(sourceNamespace);
                MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
                Map<String, String> resolved = new HashMap<>();
                resolver.getCurrentMap(sourceNamespace).getClasses().stream()
                    .flatMap(cls -> Stream.concat(Stream.of(cls), Stream.concat(cls.getFields().stream(), cls.getMethods().stream()))
                        .filter(node -> prefixes.stream().anyMatch(node.getOriginal()::startsWith))
                        .map(node -> Pair.of(node.getOriginal(), node.getMapped())))
                    .forEach(pair -> {
                        String original = pair.getFirst();
                        if (resolved.containsKey(original)) {
                            excludedNames.add(original);
                            resolved.remove(original);
                        }
                        if (!excludedNames.contains(original)) {
                            resolved.put(original, pair.getSecond());
                        }
                    });
                FLAT_MAPPINGS_CACHE.put(sourceNamespace, resolved);
                return resolved;
            }
        }
        return map;
    }

    public static List<FabricModPath> transform(List<TransformableJar> jars, List<Path> libs) {
        List<FabricModPath> transformed = new ArrayList<>();

        List<Path> inputLibs = new ArrayList<>(libs);
        List<TransformableJar> needTransforming = new ArrayList<>();
        for (TransformableJar jar : jars) {
            if (jar.cacheFile().isUpToDate()) {
                transformed.add(jar.modPath());
            }
            else {
                needTransforming.add(jar);
            }
            inputLibs.add(jar.input().toPath());
        }

        if (!needTransforming.isEmpty()) {
            List<Path> allLibs = Stream.concat(inputLibs.stream(), RENAMER_LIBS.stream()).toList();
            transformed.addAll(transformJars(needTransforming, allLibs));
        }

        return transformed;
    }

    public static TransformableJar cacheTransformableJar(File input) throws IOException {
        Files.createDirectories(ConnectorUtil.CONNECTOR_FOLDER);
        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = ConnectorUtil.CONNECTOR_FOLDER.resolve(name + MAPPED_SUFFIX + ".jar");

        FabricModFileMetadata metadata = readModMetadata(input);
        FabricModPath path = new FabricModPath(output, metadata);
        ConnectorUtil.CacheFile cacheFile = ConnectorUtil.getCached(input.toPath(), output);
        return new TransformableJar(input, path, cacheFile);
    }

    private static List<FabricModPath> transformJars(List<TransformableJar> paths, List<Path> libs) {
        // Pregenerate mappings
        if (remapper == null) {
            MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
            resolver.getMap("srg", "intermediary");
            resolver.getMap("intermediary", "srg");
            remapper = new SrgRemappingReferenceMapper(resolver.getCurrentMap("intermediary"));
        }
        if (adapterPatches == null) {
            Path path = EmbeddedDependencies.getAdapterData();
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement json = new Gson().fromJson(reader, JsonElement.class);
                MixinRemaper.setMatcherRemapper(ASMAPI::mapMethod);
                adapterPatches = PatchSerialization.deserialize(json, JsonOps.INSTANCE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Transforming Jars", paths.size());
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(paths.size());
            ClassProvider classProvider = ClassProvider.fromPaths(libs.toArray(Path[]::new));
            Transformer remappingTransformer = RelocatingRenamingTransformer.create(classProvider, s -> {}, FabricLoaderImpl.INSTANCE.getMappingResolver().getCurrentMap(SOURCE_NAMESPACE), getFlatMapping(SOURCE_NAMESPACE));
            List<Future<FabricModPath>> futures = paths.stream()
                .map(jar -> executorService.submit(() -> {
                    FabricModPath path = jar.transform(remappingTransformer);
                    progress.increment();
                    return path;
                }))
                .toList();
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for jar remap");
            }
            List<FabricModPath> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Throwable t) {
                        throw ConnectorEarlyLoader.createGenericLoadingException(t, "Error transforming jar");
                    }
                })
                .filter(Objects::nonNull)
                .toList();
            stopwatch.stop();
            LOGGER.debug(TRANSFORM_MARKER, "Processed all jars in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return results;
        } catch (InterruptedException ignored) {
            return List.of();
        } finally {
            progress.complete();
        }
    }

    private static void transformJar(File input, Path output, FabricModFileMetadata metadata, Transformer remappingTransformer) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        String jarMapping = metadata.manifestAttributes().getValue(FABRIC_MAPPING_NAMESPACE);
        if (jarMapping != null && !jarMapping.equals(SOURCE_NAMESPACE)) {
            LOGGER.error("Found transformable jar with unsupported mapping {}, currently only {} is supported", jarMapping, SOURCE_NAMESPACE);
        }

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        RefmapRemapper.RefmapFiles refmap = RefmapRemapper.processRefmaps(input, metadata.refmaps(), remapper);
        Renamer.Builder builder = Renamer.builder()
            .add(new FieldToMethodTransformer(metadata.modMetadata().getAccessWidener(), resolver.getMap("srg", SOURCE_NAMESPACE)))
            .add(remappingTransformer)
            .add(new MixinPatchTransformer(metadata.mixinClasses(), refmap.merged().mappings, adapterPatches))
            .add(new RefmapRemapper(metadata.mixinConfigs(), refmap.files()))
            .add(new ModMetadataGenerator(metadata.modMetadata().getId()))
            .logger(s -> LOGGER.trace(TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(TRANSFORM_MARKER, s));
        if (!metadata.containsAT()) {
            builder.add(new AccessWidenerTransformer(metadata.modMetadata().getAccessWidener(), resolver, getFlatMapping(SOURCE_NAMESPACE)));
        }
        try (Renamer renamer = builder.build()) {
            renamer.run(input, output.toFile());
        } catch (Throwable t) {
            LOGGER.error("Encountered error while transforming jar file " + input.getAbsolutePath(), t);
            throw t;
        }

        stopwatch.stop();
        LOGGER.debug(TRANSFORM_MARKER, "Jar {} transformed in {} ms", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static FabricModFileMetadata readModMetadata(File input) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            ConnectorLoaderModMetadata metadata;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(ConnectorUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);
                metadata = new ConnectorLoaderModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(FabricLoader.getInstance().getEnvironmentType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            boolean containsAT = jarFile.getEntry(AccessWidenerTransformer.AT_PATH) != null;

            Set<String> refmaps = new HashSet<>();
            Set<String> classes = new HashSet<>();
            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("refmap")) {
                            String refmap = json.get("refmap").getAsString();
                            refmaps.add(refmap);
                        }
                        if (json.has("package")) {
                            String pkg = json.get("package").getAsString();
                            String pkgPath = pkg.replace('.', '/') + '/';
                            Set.of("mixins", "client", "server").stream()
                                .flatMap(str -> {
                                    JsonArray array = json.getAsJsonArray(str);
                                    return Optional.ofNullable(array).stream()
                                        .flatMap(arr -> arr.asList().stream()
                                            .map(JsonElement::getAsString));
                                })
                                .map(side -> pkgPath + side.replace('.', '/'))
                                .forEach(classes::add);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
                        throw new UncheckedIOException(e);
                    }
                }
            }
            Attributes manifestAttributes = Optional.ofNullable(jarFile.getManifest()).map(Manifest::getMainAttributes).orElseGet(Attributes::new);
            return new FabricModFileMetadata(metadata, configs, refmaps, classes, manifestAttributes, containsAT);
        }
    }

    private JarTransformer() {}

    public record FabricModPath(Path path, FabricModFileMetadata metadata) {}

    public record FabricModFileMetadata(ConnectorLoaderModMetadata modMetadata, Collection<String> mixinConfigs, Set<String> refmaps, Set<String> mixinClasses, Attributes manifestAttributes, boolean containsAT) {}

    public record TransformableJar(File input, FabricModPath modPath, ConnectorUtil.CacheFile cacheFile) {
        public FabricModPath transform(Transformer remappingTransformer) throws IOException {
            Files.deleteIfExists(this.modPath.path);
            transformJar(this.input, this.modPath.path, this.modPath.metadata(), remappingTransformer);
            this.cacheFile.save();
            return this.modPath;
        }
    }
}
