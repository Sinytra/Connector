package org.sinytra.connector.transformer.jar;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.fart.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public final class JarTransformer {
    public static final String SOURCE_NAMESPACE = "intermediary";
    public static final String OBF_NAMESPACE = "mojang";
    public static final Marker TRANSFORM_MARKER = MarkerFactory.getMarker("TRANSFORM");
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

        FabricModFileMetadata metadata = FabricJarReader.readModMetadata(input, this.environment);
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
            List<Pair<File, Future<Pair<FabricModPath, AuditTrail>>>> futures = paths.stream()
                .map(jar -> {
                    Future<Pair<FabricModPath, AuditTrail>> future = executorService.submit(() -> {
                        Pair<FabricModPath, AuditTrail> pair = jar.transform(transformInstance);
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
                        Pair<FabricModPath, AuditTrail> result = pair.getSecond().get();
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

    public record TransformedFabricModPath(Path input, FabricModPath output, @Nullable AuditTrail auditTrail) {}

    public record TransformableJar(File input, FabricModPath modPath, TransformerUtil.CacheFile cacheFile, String moduleName) {
        public Pair<FabricModPath, AuditTrail> transform(JarTransformInstance transformInstance) throws IOException {
            Files.deleteIfExists(this.modPath.path);
            AuditTrail audit = transformInstance.transformJar(this.input, this.modPath.path, this.modPath.metadata());
            this.cacheFile.save();
            return Pair.of(this.modPath, audit);
        }

        public TransformedFabricModPath toTransformedPath() {
            return new TransformedFabricModPath(this.input.toPath(), this.modPath, null);
        }
    }
}
