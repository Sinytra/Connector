package org.sinytra.connector.locator.transform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.art.api.ClassProvider;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.fml.loading.mixin.FMLMixinService;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.BytecodeProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.transformer.TransformerBytecodeProvider;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.jar.SimpleClassLookup;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;
import org.sinytra.connector.util.ConnectorUtil;
import org.sinytra.connector.util.GameCodeRetriever;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class ConnectorTransformerEnvironment implements TransformerEnvironment {
    // Keep this outside of BytecodeFixerUpperFrontend to prevent unnecessary static init of patches when we only need the jar path
    private static final String GENERATED_JAR_PATH = "adapter/adapter_generated_mixins.jar";
    private static final String AUDIT_REPORT_PATH = "patch_audit.txt";

    private static final MethodHandle BYTECODE_PROVIDER_CTR;
    
    private final String mcVersion;
    @Nullable
    private final IModFile coremodsFile;

    static {
        try {
            Class<?> bytecodeProviderClass = Class.forName("net.neoforged.fml.loading.mixin.FMLClassBytecodeProvider");
            MethodHandles.Lookup provLookup = MethodHandles.privateLookupIn(bytecodeProviderClass, MethodHandles.lookup());
            BYTECODE_PROVIDER_CTR = provLookup.findConstructor(bytecodeProviderClass, MethodType.methodType(void.class, BytecodeProvider.class, FMLMixinClassProcessor.class));
        } catch (Exception e) {
            throw new RuntimeException("Error reflecting into FMLClassBytecodeProvider", e);
        }
    }

    public ConnectorTransformerEnvironment(String mcVersion, @Nullable IModFile coremodsFile) {
        this.mcVersion = mcVersion;
        this.coremodsFile = coremodsFile;
    }

    @Override
    public Path getAuditReportPath() {
        return getCacheDir().resolve(AUDIT_REPORT_PATH);
    }

    @Override
    public Path getGeneratedJarPath() {
        return getCacheDir().resolve(GENERATED_JAR_PATH);
    }

    @Override
    public ClassLookup getCleanClassLookup() {
        try {
            Path cleanJarPath = GameCodeRetriever.getCleanMinecraftJar(this.mcVersion, this);
            return new SimpleClassLookup(ClassProvider.fromPaths(cleanJarPath));
        } catch (Exception e) {
            throw new ModLoadingException(ConnectorEarlyLoader.createGenericLoadingIssue(e, "Failed to retrieve clean game code"));
        }
    }

    @Override
    public ClassProvider getRuntimeClassProvider(List<Path> libraries) {
        ClassProvider classProvider = ClassProvider.fromPaths(libraries.toArray(Path[]::new));
        if (this.coremodsFile != null) {
            return EarlyCoremodTransformer.create(classProvider, this.coremodsFile);
        }
        return classProvider;
    }

    @Override
    public TransformProgressMeter createProgressMeter(String msg, int steps) {
        return new FMLProgressMeter(StartupNotificationManager.prependProgressBar(msg, steps));
    }

    @Override
    public Path createCachedJarPath(String name) throws IOException {
        Path dir = getCacheDir();
        Files.createDirectories(dir);
        return dir.resolve(name + ".jar");
    }

    @Override
    public EnvType getEnvType() {
        return FabricLoader.getInstance().getEnvironmentType();
    }

    @Override
    public void setGlobalBytecodeLoader(@Nullable TransformerBytecodeProvider loader) {
        try {
            FMLMixinService service = (FMLMixinService) MixinService.getService();

            IClassBytecodeProvider newProvider;
            if (loader != null) {
                FMLMixinClassProcessor processor = new FMLMixinClassProcessor(service);
                BytecodeProvider bytecodeProvider = loader::getByteCode;
                newProvider = (IClassBytecodeProvider) BYTECODE_PROVIDER_CTR.invoke(bytecodeProvider, processor);
            } else {
                newProvider = null;
            }

            service.setBytecodeProvider(newProvider);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public RuntimeException onTransformationError(String message, Throwable cause) {
        return new ModLoadingException(ConnectorEarlyLoader.createGenericLoadingIssue(cause, message));
    }

    @Override
    public int getFabricMixinCompatibility(LoaderModMetadata metadata) {
        return MixinCompatibility.getMixinCompat(metadata);
    }

    @Override
    public String getJarCacheVersion() {
        return ConnectorUtil.getJarCacheVersion();
    }

    private static Path getCacheDir() {
        return FMLPaths.GAMEDIR.get().resolve(".cache/connector");
    }

    private record FMLProgressMeter(ProgressMeter handle) implements TransformProgressMeter {
        @Override
        public void increment() {
            this.handle.increment();
        }

        @Override
        public void complete() {
            this.handle.complete();
        }
    }
}
