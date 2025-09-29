package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.minecraftforge.fart.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public final class JarTransformer {
    public static final String SOURCE_NAMESPACE = "intermediary";
    public static final String OBF_NAMESPACE = "mojang";
    public static final Marker TRANSFORM_MARKER = MarkerFactory.getMarker("TRANSFORM");
    private static final String LOOM_GENERATED_PROPERTY = "fabric-loom:generated";
    private static final String LOOM_REMAP_ATTRIBUTE = "Fabric-Loom-Remap";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TransformerEnvironment environment;

    public JarTransformer(TransformerEnvironment environment) {
        this.environment = environment;
    }

    public List<TransformedFabricModPath> transform(List<TransformableJar> jars, List<Path> libs) {
        List<TransformedFabricModPath> transformed = new ArrayList<>();

        List<Path> inputLibs = new ArrayList<>(libs);
        List<TransformableJar> needTransforming = new ArrayList<>();
        for (TransformableJar jar : jars) {
            if (jar.cacheFile().isUpToDate()) {
                transformed.add(jar.toTransformedPath());
            }
            else {
                needTransforming.add(jar);
            }
            inputLibs.add(jar.input().toPath());
        }

        if (!needTransforming.isEmpty()) {
            transformed.addAll(transformJars(needTransforming, inputLibs));
        }

        return transformed;
    }

    public TransformableJar cacheTransformableJar(File input) throws IOException {
        String name = input.getName().split("\\.(?!.*\\.)")[0];
        Path output = this.environment.createCachedJarPath(name);

        FabricModFileMetadata metadata = readModMetadata(input);
        FabricModPath path = new FabricModPath(output, metadata);
        TransformerUtil.CacheFile cacheFile = TransformerUtil.getCached(input.toPath(), output, this.environment.getJarCacheVersion());
        String moduleName = getModuleName(input.toPath());
        return new TransformableJar(input, path, cacheFile, moduleName);
    }

    private List<TransformedFabricModPath> transformJars(List<TransformableJar> paths, List<Path> libs) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        TransformProgressMeter progress = this.environment.createProgressMeter("[Connector] Transforming Jars", paths.size());
        try {
            TransformProgressMeter initProgress = this.environment.createProgressMeter("[Connector] Initializing Transformer", 0);
            JarTransformInstance transformInstance;
            try {
                ClassProvider classProvider = this.environment.getRuntimeClassProvider(libs);
                ILaunchPluginService.ITransformerLoader loader = name -> classProvider.getClassBytes(name.replace('.', '/')).orElseThrow(() -> new ClassNotFoundException(name));
                this.environment.setGlobalBytecodeLoader(loader);
                transformInstance = new JarTransformInstance(this.environment, classProvider, libs);
            } finally {
                initProgress.complete();
            }
            ExecutorService executorService = Executors.newFixedThreadPool(paths.size());
            List<Pair<File, Future<Pair<FabricModPath, PatchAuditTrail>>>> futures = paths.stream()
                .map(jar -> {
                    Future<Pair<FabricModPath, PatchAuditTrail>> future = executorService.submit(() -> {
                        Pair<FabricModPath, PatchAuditTrail> pair = jar.transform(transformInstance);
                        progress.increment();
                        return pair;
                    });
                    return Pair.of(jar.input(), future);
                })
                .toList();
            executorService.shutdown();
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
                throw new RuntimeException("Timed out waiting for jar remap");
            }
            List<TransformedFabricModPath> results = futures.stream()
                .map(pair -> {
                    try {
                        Pair<FabricModPath, PatchAuditTrail> result = pair.getSecond().get();
                        return new TransformedFabricModPath(pair.getFirst().toPath(), result.getFirst(), result.getSecond());
                    } catch (Throwable t) {
                        throw this.environment.onTransformationError("Error transforming file " + pair.getFirst().getName(), t);
                    }
                })
                .filter(Objects::nonNull)
                .toList();
            uncheck(() -> transformInstance.getBfu().saveGeneratedAdapterJar());
            transformInstance.saveAuditReport();
            stopwatch.stop();
            LOGGER.debug(TRANSFORM_MARKER, "Processed all jars in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return results;
        } catch (InterruptedException ignored) {
            return List.of();
        } finally {
            cleanupEnvironment();
            progress.complete();
        }
    }

    private FabricModFileMetadata readModMetadata(File input) throws IOException {
        try (JarFile jarFile = new JarFile(input)) {
            LoaderModMetadata metadata;
            Set<String> configs;
            try (InputStream ins = jarFile.getInputStream(jarFile.getEntry(TransformerUtil.FABRIC_MOD_JSON))) {
                LoaderModMetadata rawMetadata = ModMetadataParser.parseMetadata(ins, "", Collections.emptyList(), this.environment.getVersionOverrides(), this.environment.getDependencyOverrides().get(), false);
                metadata = this.environment.wrapModMetadata(rawMetadata);

                configs = new HashSet<>(metadata.getMixinConfigs(this.environment.getEnvType()));
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            boolean containsAT = jarFile.getEntry(TransformerUtil.AT_PATH) != null;

            Set<String> refmaps = new HashSet<>();
            Set<String> mixinPackages = new HashSet<>();
            Set<String> mixinClasses = new HashSet<>();
            for (String configName : configs) {
                ZipEntry entry = jarFile.getEntry(configName);
                if (entry != null) {
                    readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages, mixinClasses);
                }
            }
            // Find additional configs that may not be listed in mod metadata
            jarFile.stream()
                .forEach(entry -> {
                    String name = entry.getName();
                    if ((name.endsWith(".mixins.json") || name.startsWith("mixins.") && name.endsWith(".json")) && configs.add(name)) {
                        readMixinConfigPackages(input, jarFile, entry, refmaps, mixinPackages, mixinClasses);
                    }
                });
            Attributes manifestAttributes = Optional.ofNullable(jarFile.getManifest()).map(Manifest::getMainAttributes).orElseGet(Attributes::new);
            boolean generated = isGeneratedLibraryJarMetadata(manifestAttributes, metadata);
            return new FabricModFileMetadata(metadata, Set.copyOf(configs), configs, refmaps, mixinPackages, mixinClasses, manifestAttributes, containsAT, generated);
        }
    }

    private static boolean isGeneratedLibraryJarMetadata(Attributes manifestAttributes, LoaderModMetadata metadata) {
        CustomValue generatedValue = metadata.getCustomValue(LOOM_GENERATED_PROPERTY);
        if (generatedValue != null && generatedValue.getType() == CustomValue.CvType.BOOLEAN && generatedValue.getAsBoolean()) {
            String loomRemapAttribute = manifestAttributes.getValue(LOOM_REMAP_ATTRIBUTE);
            return loomRemapAttribute == null || !loomRemapAttribute.equals("true");
        }
        return false;
    }

    private static void readMixinConfigPackages(File input, JarFile jarFile, ZipEntry entry, Set<String> refmaps, Set<String> packages, Set<String> mixinClasses) {
        try (Reader reader = new InputStreamReader(jarFile.getInputStream(entry))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("refmap")) {
                String refmap = json.get("refmap").getAsString();
                refmaps.add(refmap);
            }
            if (json.has("package")) {
                String pkg = json.get("package").getAsString();
                if (!pkg.isEmpty()) {
                    String pkgPath = pkg.replace('.', '/') + '/';
                    packages.add(pkgPath);
                }
                for (String type : List.of("mixins", "client", "server")) {
                    if (json.has(type)) {
                        for (JsonElement mixin : json.getAsJsonArray(type)) {
                            String className = pkg + "." + mixin.getAsString();
                            mixinClasses.add(className.replace('.', '/'));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error reading mixin config entry {} in file {}", entry.getName(), input.getAbsolutePath());
            throw new RuntimeException(t);
        }
    }

    @SuppressWarnings("unchecked")
    private void cleanupEnvironment() {
        this.environment.setGlobalBytecodeLoader(null);
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClassInfo.class, MethodHandles.lookup());

            // Remove cached class infos, which might not be up-to-date
            VarHandle cacheField = lookup.findStaticVarHandle(ClassInfo.class, "cache", Map.class);
            Map<String, ClassInfo> cache = (Map<String, ClassInfo>) cacheField.get();
            cache.clear();

            VarHandle objectField = lookup.findStaticVarHandle(ClassInfo.class, "OBJECT", ClassInfo.class);
            ClassInfo object = (ClassInfo) objectField.get();

            cache.put("java/lang/Object", object);
        } catch (Throwable t) {
            LOGGER.error("Error cleaning up after jar transformation", t);
        }
    }

    @Nullable
    private static String getModuleName(Path path) {
        try(JarContents contents = new JarContentsBuilder().paths(path).build()) {
            JarMetadata metadata = JarMetadata.from(contents);
            return metadata.descriptor().name();
        } catch (IOException e) {
            LOGGER.error("Error reading jar contents from {}", path, e);
            return null;
        }
    }

    public record FabricModPath(Path path, FabricModFileMetadata metadata) {}

    public record TransformedFabricModPath(Path input, FabricModPath output, @Nullable PatchAuditTrail auditTrail) {}

    public record FabricModFileMetadata(LoaderModMetadata modMetadata, Collection<String> visibleMixinConfigs, Collection<String> mixinConfigs, Set<String> refmaps, Set<String> mixinPackages, Set<String> mixinClasses, Attributes manifestAttributes, boolean containsAT, boolean generated) {}

    public record TransformableJar(File input, FabricModPath modPath, TransformerUtil.CacheFile cacheFile, String moduleName) {
        public Pair<FabricModPath, PatchAuditTrail> transform(JarTransformInstance transformInstance) throws IOException {
            Files.deleteIfExists(this.modPath.path);
            PatchAuditTrail audit = transformInstance.transformJar(this.input, this.modPath.path, this.modPath.metadata());
            this.cacheFile.save();
            return Pair.of(this.modPath, audit);
        }

        public TransformedFabricModPath toTransformedPath() {
            return new TransformedFabricModPath(this.input.toPath(), this.modPath, null);
        }
    }
}
