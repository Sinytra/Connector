package dev.su5ed.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import dev.su5ed.sinytra.connector.locator.DependencyResolver;
import dev.su5ed.sinytra.connector.transformer.AccessWidenerTransformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public final class JarTransformer {
    public static final String SOURCE_NAMESPACE = "intermediary";
    public static final String OBF_NAMESPACE = "srg";
    public static final Marker TRANSFORM_MARKER = MarkerFactory.getMarker("TRANSFORM");
    private static final String MAPPED_SUFFIX = "_mapped_" + FMLEnvironment.naming + "_" + FMLLoader.versionInfo().mcVersion();
    // Keep this outside of BytecodeFixerUpperFrontend to prevent unnecessary static init of patches when we only need the jar path
    private static final Path GENERATED_JAR_PATH = ConnectorUtil.CONNECTOR_FOLDER.resolve("adapter/adapter_generated_mixins.jar");
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final VarHandle TRANSFORMER_LOADER_FIELD = uncheck(() -> MethodHandles.privateLookupIn(MixinLaunchPluginLegacy.class, MethodHandles.lookup()).findVarHandle(MixinLaunchPluginLegacy.class, "transformerLoader", ILaunchPluginService.ITransformerLoader.class));

    private static void setMixinClassProvider(ILaunchPluginService.ITransformerLoader loader) {
        try {
            MixinLaunchPluginLegacy plugin = (MixinLaunchPluginLegacy) MixinService.getService().getBytecodeProvider();
            TRANSFORMER_LOADER_FIELD.set(plugin, loader);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Path getGeneratedJarPath() {
        return GENERATED_JAR_PATH;
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
            List<Path> renamerLibs = Stream.of(FMLLoader.getLaunchHandler().getMinecraftPaths())
                .flatMap(paths -> Stream.concat(paths.minecraftPaths().stream(), paths.otherArtifacts().stream()))
                .toList();
            List<Path> allLibs = Stream.concat(inputLibs.stream(), renamerLibs.stream()).toList();
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
        Stopwatch stopwatch = Stopwatch.createStarted();
        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Transforming Jars", paths.size());
        try {
            ClassProvider classProvider = ClassProvider.fromPaths(libs.toArray(Path[]::new));
            ILaunchPluginService.ITransformerLoader loader = name -> classProvider.getClassBytes(name.replace('.', '/')).orElseThrow(() -> new ClassNotFoundException(name));
            setMixinClassProvider(loader);

            JarTransformInstance transformInstance = new JarTransformInstance(classProvider);
            ExecutorService executorService = Executors.newFixedThreadPool(paths.size());
            List<Pair<File, Future<FabricModPath>>> futures = paths.stream()
                .map(jar -> {
                    Future<FabricModPath> future = executorService.submit(() -> {
                        FabricModPath path = jar.transform(transformInstance);
                        progress.increment();
                        return path;
                    });
                    return Pair.of(jar.input(), future);
                })
                .toList();
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for jar remap");
            }
            List<FabricModPath> results = futures.stream()
                .map(pair -> {
                    try {
                        return pair.getSecond().get();
                    } catch (Throwable t) {
                        throw ConnectorEarlyLoader.createGenericLoadingException(t, "Error transforming jar " + pair.getFirst().getAbsolutePath());
                    }
                })
                .filter(Objects::nonNull)
                .toList();
            uncheck(() -> transformInstance.getBfu().saveGeneratedAdapterJar());
            stopwatch.stop();
            LOGGER.debug(TRANSFORM_MARKER, "Processed all jars in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return results;
        } catch (InterruptedException ignored) {
            return List.of();
        } finally {
            setMixinClassProvider(null);
            progress.complete();
        }
    }

    private static FabricModFileMetadata readModMetadata(File input) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            ConnectorLoaderModMetadata metadata;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(ConnectorUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), DependencyResolver.VERSION_OVERRIDES, DependencyResolver.DEPENDENCY_OVERRIDES, false);
                metadata = new ConnectorLoaderModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(FabricLoader.getInstance().getEnvironmentType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            boolean containsAT = jarFile.getEntry(AccessWidenerTransformer.AT_PATH) != null;

            Set<String> refmaps = new HashSet<>();
            Set<String> mixinPackages = new HashSet<>();
            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages);
                }
            }
            // Find additional configs that may not be listed in mod metadata
            jarFile.stream()
                .forEach(entry -> {
                    String name = entry.getName();
                    if ((name.endsWith(".mixins.json") || name.startsWith("mixins.") && name.endsWith(".json")) && configs.add(name)) {
                        readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages);
                    }
                });
            Attributes manifestAttributes = Optional.ofNullable(jarFile.getManifest()).map(Manifest::getMainAttributes).orElseGet(Attributes::new);
            return new FabricModFileMetadata(metadata, configs, refmaps, mixinPackages, manifestAttributes, containsAT);
        }
    }

    private static void readMixinConfigPackages(File input, JarFile jarFile, ZipEntry entry, Set<String> refmaps, Set<String> packages) {
        try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("refmap")) {
                String refmap = json.get("refmap").getAsString();
                refmaps.add(refmap);
            }
            if (json.has("package")) {
                String pkg = json.get("package").getAsString();
                String pkgPath = pkg.replace('.', '/') + '/';
                packages.add(pkgPath);
            }
        } catch (IOException e) {
            LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
            throw new UncheckedIOException(e);
        }
    }

    private JarTransformer() {}

    public record FabricModPath(Path path, FabricModFileMetadata metadata) {}

    public record FabricModFileMetadata(ConnectorLoaderModMetadata modMetadata, Collection<String> mixinConfigs, Set<String> refmaps, Set<String> mixinPackages, Attributes manifestAttributes, boolean containsAT) {}

    public record TransformableJar(File input, FabricModPath modPath, ConnectorUtil.CacheFile cacheFile) {
        public FabricModPath transform(JarTransformInstance transformInstance) throws IOException {
            Files.deleteIfExists(this.modPath.path);
            transformInstance.transformJar(this.input, this.modPath.path, this.modPath.metadata());
            this.cacheFile.save();
            return this.modPath;
        }
    }
}
