package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Multimap;
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
import java.util.ArrayList;
import java.util.Collection;
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

    public static List<JarTransformer.TransformableJar> resolveDependencies(Collection<JarTransformer.TransformableJar> keys, Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> jars, Iterable<IModFile> loadedMods) {
        BiMap<JarTransformer.TransformableJar, ModCandidate> jarToCandidate = HashBiMap.create();
        // Fabric candidates
        List<ModCandidate> candidates = createCandidatesRecursive(keys, keys, jars, jarToCandidate);
        // Forge dependencies
        Stream<ModCandidate> forgeCandidates = StreamSupport.stream(loadedMods.spliterator(), false)
            .flatMap(modFile -> modFile.getModFileInfo() != null ? modFile.getModInfos().stream() : Stream.empty())
            .map(modInfo -> ModCandidate.createPlain(List.of(modInfo.getOwningFile().getFile().getFilePath()), new BuiltinMetadataWrapper(new FMLModMetadata(modInfo)), false, List.of()));
        Stream<ModCandidate> builtinCandidates = Stream.of(createJavaMod(), createFabricLoaderMod());
        // Merge
        List<ModCandidate> allCandidates = Stream.of(candidates.stream(), forgeCandidates, builtinCandidates).flatMap(Function.identity()).toList();

        EnvType envType = FabricLoader.getInstance().getEnvironmentType();
        try {
            List<ModCandidate> resolved = ModResolver.resolve(allCandidates, envType, Map.of());
            List<JarTransformer.TransformableJar> candidateJars = resolved.stream()
                .map(jarToCandidate.inverse()::get)
                .filter(Objects::nonNull)
                .toList();
            LOGGER.info("Dependency resolution found {} candidates to load", candidateJars.size());
            return candidateJars;
        } catch (ModResolutionException e) {
            throw ConnectorEarlyLoader.createLoadingException(e, e.getMessage().replaceAll("\t", "  "));
        }
    }

    private static List<ModCandidate> createCandidatesRecursive(Collection<JarTransformer.TransformableJar> candidateJars, Collection<JarTransformer.TransformableJar> jarsToLoad, Multimap<JarTransformer.TransformableJar, JarTransformer.TransformableJar> parentsToChildren, Map<JarTransformer.TransformableJar, ModCandidate> jarToCandidate) {
        List<ModCandidate> candidates = new ArrayList<>();
        for (JarTransformer.TransformableJar candidateJar : candidateJars) {
            if (jarsToLoad.contains(candidateJar)) {
                ModCandidate candidate = jarToCandidate.computeIfAbsent(candidateJar, j -> {
                    Collection<JarTransformer.TransformableJar> children = parentsToChildren.containsKey(candidateJar) ? parentsToChildren.get(candidateJar) : List.of();
                    List<ModCandidate> childCandidates = createCandidatesRecursive(children, jarsToLoad, parentsToChildren, jarToCandidate);
                    List<Path> paths = parentsToChildren.containsValue(candidateJar) ? null : List.of(candidateJar.modPath().path());
                    ModCandidate parent = ModCandidate.createPlain(paths, candidateJar.modPath().metadata().modMetadata(), false, childCandidates);
                    for (ModCandidate childCandidate : childCandidates) {
                        childCandidate.addParent(parent);
                    }
                    return parent;
                });
                candidates.add(candidate);
            }
        }
        return candidates;
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
