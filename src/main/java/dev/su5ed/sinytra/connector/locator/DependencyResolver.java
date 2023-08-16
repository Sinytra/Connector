package dev.su5ed.sinytra.connector.locator;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.transformer.JarTransformer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.FMLModMetadata;
import net.fabricmc.loader.impl.discovery.BuiltinMetadataWrapper;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.discovery.ModResolutionException;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public final class DependencyResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final VersionOverrides VERSION_OVERRIDES = new VersionOverrides();
    public static final DependencyOverrides DEPENDENCY_OVERRIDES = new DependencyOverrides(FMLPaths.CONFIGDIR.get());

    public static void resolveDependencies(List<JarTransformer.TransformableJar> jars, Iterable<IModFile> loadedMods) {
        // Fabric candidates
        Stream<ModCandidate> candidates = jars.stream()
            .map(jar -> ModCandidate.createPlain(List.of(jar.modPath().path()), jar.modPath().metadata().modMetadata(), false, List.of()));
        // Forge dependencies
        Stream<ModCandidate> forgeCandidates = StreamSupport.stream(loadedMods.spliterator(), false)
            .flatMap(modFile -> modFile.getModFileInfo() != null ? modFile.getModInfos().stream() : Stream.empty())
            .map(modInfo -> ModCandidate.createPlain(List.of(modInfo.getOwningFile().getFile().getFilePath()), new BuiltinMetadataWrapper(new FMLModMetadata(modInfo)), false, List.of()));
        Stream<ModCandidate> builtinCandidates = Stream.of(createJavaMod(), createFabricLoaderMod());
        // Merge
        List<ModCandidate> allCandidates = Stream.of(candidates, forgeCandidates, builtinCandidates).flatMap(Function.identity()).toList();

        EnvType envType = FabricLoader.getInstance().getEnvironmentType();
        try {
            ModResolver.resolve(allCandidates, envType, Map.of());
            LOGGER.info("Dependency resolution completed successfully");
        } catch (ModResolutionException e) {
            throw ConnectorEarlyLoader.createLoadingException(e, e.getMessage().replaceAll("\n\t", "\n  "));
        }
    }

    private static ModCandidate createJavaMod() {
        ModMetadata metadata = new BuiltinModMetadata.Builder("java", System.getProperty("java.specification.version").replaceFirst("^1\\.", ""))
            .setName(System.getProperty("java.vm.name"))
            .build();
        GameProvider.BuiltinMod builtinMod = new GameProvider.BuiltinMod(Collections.singletonList(Paths.get(System.getProperty("java.home"))), metadata);

        return ModCandidate.createBuiltin(builtinMod, VERSION_OVERRIDES, DEPENDENCY_OVERRIDES);
    }

    private static ModCandidate createFabricLoaderMod() {
        ModMetadata metadata = new BuiltinModMetadata.Builder("fabricloader", Objects.requireNonNullElse(FabricLoader.class.getPackage().getImplementationVersion(), "0.0NONE"))
            .setName("Fabric Loader")
            .build();
        GameProvider.BuiltinMod builtinMod = new GameProvider.BuiltinMod(Collections.singletonList(Path.of(uncheck(() -> FabricLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()))), metadata);
        
        return ModCandidate.createBuiltin(builtinMod, VERSION_OVERRIDES, DEPENDENCY_OVERRIDES);
    }
}
