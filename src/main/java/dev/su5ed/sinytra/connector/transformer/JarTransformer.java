package dev.su5ed.sinytra.connector.transformer;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fml.loading.FMLLoader;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;

public final class JarTransformer {
    private static final String MAPPED_SUFFIX = "_mapped_official_" + FMLLoader.versionInfo().mcVersion();
    private static final String FABRIC_MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
    // Increment to invalidate cache
    private static final int CACHE_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker TRANSFORM_MARKER = MarkerFactory.getMarker("TRANSFORM");

    private static final Map<String, Map<String, String>> FLAT_MAPPINGS_CACHE = new HashMap<>();

    private static SrgRemappingReferenceMapper remapper;

    private static Map<String, String> getFlatMapping(String sourceNamespace) {
        Map<String, String> map = FLAT_MAPPINGS_CACHE.get(sourceNamespace);
        if (map == null) {
            synchronized (JarTransformer.class) {
                Map<String, String> existing = FLAT_MAPPINGS_CACHE.get(sourceNamespace);
                if (existing != null) {
                    return existing;
                }

                LOGGER.debug(TRANSFORM_MARKER, "Creating flat mapping for namespace {}", sourceNamespace);
                MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
                Map<String, String> resolved = resolver.getCurrentMap(sourceNamespace).getClasses().stream()
                    .flatMap(cls -> {
                        Pair<String, String> clsRename = Pair.of(cls.getOriginal(), cls.getMapped());
                        Stream<Pair<String, String>> fieldRenames = cls.getFields().stream().map(field -> Pair.of(field.getOriginal(), field.getMapped()));
                        Stream<Pair<String, String>> methodRenames = cls.getMethods().stream().map(method -> Pair.of(method.getOriginal(), method.getMapped()));
                        return Stream.concat(Stream.of(clsRename), Stream.concat(fieldRenames, methodRenames));
                    })
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));
                FLAT_MAPPINGS_CACHE.put(sourceNamespace, resolved);
                return resolved;
            }
        }
        return map;
    }

    public static List<FabricModPath> transformPaths(List<Path> paths) {
        return transform(paths.stream().map(rethrowFunction(p -> cacheTransformableJar(p.toFile()))).toList());
    }

    public static List<FabricModPath> transform(List<TransformableJar> jars) {
        List<FabricModPath> transformed = new ArrayList<>();

        List<TransformableJar> needTransforming = new ArrayList<>();
        for (TransformableJar jar : jars) {
            if (jar.cacheFile().isUpToDate()) {
                transformed.add(jar.modPath());
            }
            else {
                needTransforming.add(jar);
            }
        }

        if (!needTransforming.isEmpty()) {
            transformed.addAll(transformJars(needTransforming));
        }

        return transformed;
    }

    public static TransformableJar cacheTransformableJar(File input) throws IOException {
        Files.createDirectories(ConnectorUtil.CONNECTOR_FOLDER);
        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = ConnectorUtil.CONNECTOR_FOLDER.resolve(name + MAPPED_SUFFIX + ".jar");

        FabricModFileMetadata metadata = readModMetadata(input);
        FabricModPath path = new FabricModPath(output, metadata);
        ConnectorUtil.CacheFile cacheFile = ConnectorUtil.getCached(String.valueOf(CACHE_VERSION), input.toPath(), output);
        return new TransformableJar(input, path, cacheFile);
    }

    private static List<FabricModPath> transformJars(List<TransformableJar> paths) {
        // Pregenerate mappings
        if (remapper == null) {
            MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
            resolver.getMap("srg", "intermediary");
            resolver.getMap("intermediary", "srg");
            remapper = new SrgRemappingReferenceMapper(resolver.getCurrentMap("intermediary"));
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        ExecutorService executorService = Executors.newFixedThreadPool(paths.size());

        List<Future<FabricModPath>> futures = paths.stream()
            .map(jar -> executorService.submit(jar::transform))
            .toList();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for jar remap");
            }
        } catch (InterruptedException ignored) {
            // Dunny what I should do with this
        }

        List<FabricModPath> results = futures.stream()
            .map(rethrowFunction(Future::get))
            .toList();
        stopwatch.stop();
        LOGGER.debug(TRANSFORM_MARKER, "Processed all jars in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return results;
    }

    private static void transformJar(File input, Path output, FabricModFileMetadata metadata) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
        String fromMapping = Optional.ofNullable(metadata.manifestAttributes().getValue(FABRIC_MAPPING_NAMESPACE)).orElse("intermediary");
        Map<String, String> mappings = getFlatMapping(fromMapping);

        try (Renamer renamer = Renamer.builder()
            .add(new FieldToMethodTransformer(metadata.modMetadata().getAccessWidener(), resolver.getMap("srg", "intermediary")))
            .add(new SimpleRenamingTransformer(mappings))
            .add(new MixinReplacementTransformer(metadata.mixinClasses(), mappings))
            .add(new RefmapTransformer(metadata.mixinConfigs(), metadata.refmaps(), remapper))
            .add(new AccessWidenerTransformer(metadata.modMetadata().getAccessWidener(), resolver))
            .add(new PackMetadataGenerator(metadata.modMetadata().getId()))
            .logger(s -> LOGGER.trace(TRANSFORM_MARKER, s))
            .debug(s -> LOGGER.trace(TRANSFORM_MARKER, s))
            .build()) {
            renamer.run(input, output.toFile());
        }

        stopwatch.stop();
        LOGGER.debug(TRANSFORM_MARKER, "Jar {} transformed in {} ms", input.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static FabricModFileMetadata readModMetadata(File input) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            Attributes manifestAttributes = jarFile.getManifest().getMainAttributes();

            ConnectorLoaderModMetadata metadata;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(ConnectorUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);
                metadata = new ConnectorLoaderModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(FabricLoader.getInstance().getEnvironmentType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }

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
            return new FabricModFileMetadata(metadata, configs, refmaps, classes, manifestAttributes);
        }
    }

    private JarTransformer() {}

    public record FabricModPath(Path path, FabricModFileMetadata metadata) {}

    public record FabricModFileMetadata(ConnectorLoaderModMetadata modMetadata, Collection<String> mixinConfigs, Set<String> refmaps, Set<String> mixinClasses, Attributes manifestAttributes) {}

    public record TransformableJar(File input, FabricModPath modPath, ConnectorUtil.CacheFile cacheFile) {
        public FabricModPath transform() throws IOException {
            Files.deleteIfExists(this.modPath.path);
            transformJar(this.input, this.modPath.path, this.modPath.metadata());
            this.cacheFile.save();
            return this.modPath;
        }
    }
}
