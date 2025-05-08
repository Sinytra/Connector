package org.sinytra.connector.locator.transform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fart.api.ClassProvider;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.adapter.patch.util.provider.ZipClassLookup;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.locator.ConnectorFabricModMetadata;
import org.sinytra.connector.locator.DependencyResolver;
import org.sinytra.connector.locator.EmbeddedDependencies;
import org.sinytra.connector.service.FabricMixinBootstrap;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.jar.SimpleClassLookup;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;
import org.sinytra.connector.util.ConnectorUtil;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.rethrowFunction;
import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class ConnectorTransformerEnvironment implements TransformerEnvironment {
    // Keep this outside of BytecodeFixerUpperFrontend to prevent unnecessary static init of patches when we only need the jar path
    private static final Path GENERATED_JAR_PATH = ConnectorUtil.CONNECTOR_FOLDER.resolve("adapter/adapter_generated_mixins.jar");
    private static final Path AUDIT_REPORT_PATH = ConnectorUtil.CONNECTOR_FOLDER.resolve("patch_audit.txt");
    private static final String MAPPED_SUFFIX = "_mapped_moj_" + FMLLoader.versionInfo().mcVersion();
    private static final VarHandle TRANSFORMER_LOADER_FIELD = uncheck(() -> MethodHandles.privateLookupIn(MixinLaunchPluginLegacy.class, MethodHandles.lookup()).findVarHandle(MixinLaunchPluginLegacy.class, "transformerLoader", ILaunchPluginService.ITransformerLoader.class));

    private final Collection<IModFile> loadedModFiles;

    public ConnectorTransformerEnvironment(Collection<IModFile> loadedModFiles) {
        this.loadedModFiles = loadedModFiles;
    }

    @Override
    public Path getAuditReportPath() {
        return AUDIT_REPORT_PATH;
    }

    @Override
    public Path getGeneratedJarPath() {
        return GENERATED_JAR_PATH;
    }

    @Override
    public ClassLookup getCleanClassLookup() {
        String mcAndNeoFormVersion = FMLLoader.versionInfo().mcAndNeoFormVersion();
        if (FMLEnvironment.production) {
            MavenCoordinate coords = new MavenCoordinate("net.minecraft", FMLEnvironment.dist.isClient() ? "client" : "server", "", "srg", mcAndNeoFormVersion);
            Path path = LibraryFinder.findPathForMaven(coords);
            if (!Files.exists(path)) {
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(path));
            }
            ZipFile zipFile = uncheck(() -> new ZipFile(path.toFile()));
            return new ZipClassLookup(zipFile);
        } else {
            // Search for system property
            Path cleanPath = Optional.ofNullable(System.getProperty("connector.clean.path"))
                .map(Path::of)
                .filter(Files::exists)
                .orElseThrow(() -> new RuntimeException("Could not determine clean minecraft artifact path"));
            return new SimpleClassLookup(ClassProvider.fromPaths(cleanPath));
        }
    }

    @Override
    public ClassProvider getRuntimeClassProvider(List<Path> libraries) {
        ClassProvider classProvider = ClassProvider.fromPaths(libraries.toArray(Path[]::new));
        return EarlyJSCoremodTransformer.create(classProvider, this.loadedModFiles);
    }

    @Override
    public LoaderModMetadata wrapModMetadata(LoaderModMetadata metadata) {
        DependencyResolver.removeAliasedModDependencyConstraints(metadata);
        return new ConnectorFabricModMetadata(metadata);
    }

    @Override
    public TransformProgressMeter createProgressMeter(String msg, int steps) {
        return new FMLProgressMeter(StartupNotificationManager.prependProgressBar(msg, steps));
    }

    @Override
    public Path createCachedJarPath(String name) throws IOException {
        Files.createDirectories(ConnectorUtil.CONNECTOR_FOLDER);
        return ConnectorUtil.CONNECTOR_FOLDER.resolve(name + MAPPED_SUFFIX + ".jar");
    }

    @Override
    public EnvType getEnvType() {
        return FabricLoader.getInstance().getEnvironmentType();
    }

    @Override
    public VersionOverrides getVersionOverrides() {
        return DependencyResolver.VERSION_OVERRIDES;
    }

    @Override
    public Supplier<DependencyOverrides> getDependencyOverrides() {
        return DependencyResolver.DEPENDENCY_OVERRIDES;
    }

    @Override
    public void setGlobalBytecodeLoader(@Nullable ILaunchPluginService.ITransformerLoader loader) {
        try {
            MixinLaunchPluginLegacy plugin = (MixinLaunchPluginLegacy) MixinService.getService().getBytecodeProvider();
            TRANSFORMER_LOADER_FIELD.set(plugin, loader);
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
        return FabricMixinBootstrap.MixinConfigDecorator.getMixinCompat(metadata);
    }

    @Override
    public String getJarCacheVersion() {
        return EmbeddedDependencies.getJarCacheVersion();
    }

    @Override
    public void completeSetup() {
        // Injection point data extracted from coremods/method_redirector.js
        String[] targetClasses = this.loadedModFiles.stream()
            .filter(m -> m.getModFileInfo() != null && !m.getModInfos().isEmpty() && m.getModInfos().getFirst().getModId().equals(ConnectorUtil.NEOFORGE_MODID))
            .map(m -> m.findResource("coremods/finalize_spawn_targets.json"))
            .filter(Files::exists)
            .map(rethrowFunction(path -> {
                try (Reader reader = Files.newBufferedReader(path)) {
                    return JsonParser.parseReader(reader);
                }
            }))
            .filter(JsonElement::isJsonArray)
            .flatMap(json -> json.getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString))
            .toArray(String[]::new);
        if (targetClasses.length > 0) {
//            MixinPatchTransformer.completeSetup(List.of(
//                Patch.builder()
//                    .targetClass(targetClasses)
//                    .targetInjectionPoint("m_6518_(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;")
//                    .modifyInjectionPoint("Lnet/minecraftforge/event/ForgeEventFactory;onFinalizeSpawn(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;")
//                    .build()
//            ));
        }
    }

    @Override
    public URL getAdapterPatchDataURL() throws MalformedURLException {
        return EmbeddedDependencies.getAdapterData(EmbeddedDependencies.ADAPTER_PATCH_DATA).toUri().toURL();
    }

    @Override
    public URL getAdapterLVTDataURL() throws MalformedURLException {
        return EmbeddedDependencies.getAdapterData(EmbeddedDependencies.ADAPTER_LVT_OFFSETS).toUri().toURL();
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
