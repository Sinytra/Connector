package dev.su5ed.sinytra.connector.locator;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import dev.su5ed.sinytra.connector.transformer.JarTransformer;
import dev.su5ed.sinytra.connector.transformer.JarTransformer.FabricModPath;
import net.minecraftforge.forgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SplitPackageMerger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker MERGER = MarkerFactory.getMarker("MERGER");

    /**
     * Detect and resolve split package conflicts in jars.
     * Supplied paths must point to valid jars paths usable by {@link SecureJar}.
     * @param paths jar paths to process
     * @return a list of adjusted jar paths
     */
    public static List<FilteredModPath> mergeSplitPackages(List<FabricModPath> paths, Iterable<IModFile> existing) {
        // Paths that don't contain conflicting jars
        List<FabricModPath> plainPaths = new ArrayList<>(paths);
        // Processed paths result
        List<FilteredModPath> output = new ArrayList<>();

        // Package name -> list of jars that contain the package
        Map<String, List<Pair<SecureJar, FabricModPath>>> pkgSources = new HashMap<>();
        for (FabricModPath modInfo : paths) {
            SecureJar secureJar = SecureJar.from(modInfo.path());
            for (String pkg : secureJar.getPackages()) {
                pkgSources.computeIfAbsent(pkg, p -> new ArrayList<>()).add(Pair.of(secureJar, modInfo));
            }
        }

        // Find all jars that need merging
        // Keep track of the order jars were found in, for selecting package owners
        List<SecureJar> jarOrder = new ArrayList<>();
        // Map of packages that need merging and their sources
        Map<String, List<Pair<SecureJar, FabricModPath>>> mergePkgs = new LinkedHashMap<>();
        AtomicInteger totalJars = new AtomicInteger(0);
        pkgSources.forEach((pkg, sources) -> {
            if (sources.size() > 1) {
                LOGGER.debug(MERGER, "Found split package {} in jars {}", pkg, sources.stream().map(info -> info.getFirst().name()).collect(Collectors.joining(",")));
                sources.forEach(source -> {
                    if (plainPaths.remove(source.getSecond())) {
                        totalJars.getAndIncrement();
                    }
                    mergePkgs.computeIfAbsent(pkg, p -> new ArrayList<>()).add(source);
                    jarOrder.add(source.getFirst());
                });
            }
        });
        LOGGER.debug(MERGER, "Found {} split packages across {} jars", mergePkgs.keySet().size(), totalJars.get());

        // Name -> Jar merge info
        Map<String, JarMergeInfo> jarMap = new HashMap<>();
        mergePkgs.forEach((pkg, sources) -> {
            // Sort sources in the order jars were discovered
            sources.sort(Comparator.comparingInt(p -> jarOrder.indexOf(p.getFirst())));

            Pair<SecureJar, FabricModPath> pair = sources.get(0);
            SecureJar candidate = pair.getFirst();
            String name = candidate.name();
            JarMergeInfo owner = jarMap.computeIfAbsent(name, str -> new JarMergeInfo(candidate, pair.getSecond().metadata()));
            analyzeJar(jarMap, owner, sources.subList(1, sources.size()), pkg);
        });

        // Find packages that are already loaded, along with packages of discovered mods
        Set<String> existingPackages = new HashSet<>();
        for (IModFile modFile : existing) {
            existingPackages.addAll(modFile.getSecureJar().getPackages());
        }
        Collection<IModuleLayerManager.Layer> layers = Set.of(IModuleLayerManager.Layer.BOOT, IModuleLayerManager.Layer.SERVICE);
        IModuleLayerManager manager = Launcher.INSTANCE.findLayerManager().orElseThrow();
        for (IModuleLayerManager.Layer layer : layers) {
            manager.getLayer(layer).orElseThrow().modules().stream()
                .flatMap(module -> module.getPackages().stream())
                .forEach(existingPackages::add);
        }
        // Remove existing classpath packages
        for (String pkg : existingPackages) {
            List<Pair<SecureJar, FabricModPath>> list = pkgSources.get(pkg);
            if (list != null) {
                for (Pair<SecureJar, FabricModPath> pair : list) {
                    SecureJar jar = pair.getFirst();
                    FabricModPath modPath = pair.getSecond();
                    plainPaths.remove(modPath);
                    JarMergeInfo info = jarMap.computeIfAbsent(jar.name(), str -> new JarMergeInfo(jar, modPath.metadata()));
                    LOGGER.debug(MERGER, "Excluding existing package {} from jar {}", pkg, jar.name());
                    info.excludedPackages().add(pkg);
                }
            }
        }

        // Process gathered merge information
        jarMap.values().forEach(info -> {
            Set<Path> additionalPaths = info.additionalPaths();
            Set<String> excludedPackages = info.excludedPackages();
            BiPredicate<String, String> filter = !excludedPackages.isEmpty() ? new PackageTracker(Set.copyOf(excludedPackages)) : null;
            Path[] jarPaths = Stream.concat(Stream.of(info.jar().getPrimaryPath()), additionalPaths.stream()).toArray(Path[]::new);
            output.add(new FilteredModPath(jarPaths, filter, info.metadata));
        });

        // Add unprocessed paths to output
        for (FabricModPath modPath : plainPaths) {
            output.add(new FilteredModPath(new Path[] { modPath.path() }, null, modPath.metadata()));
        }
        if (paths.size() != output.size()) {
            LOGGER.error("Expected {} paths, got {}", paths.size(), plainPaths.size());
            throw new IllegalStateException("Path size disprenancy detected!");
        }

        return output;
    }

    /**
     * Determines which packages to remove from / add to a jar given a package owner and other package sources.
     *
     * @param swap   jar merge info map to write information to, must be mutable
     * @param master the package owner jar
     * @param others the remaining package sources
     * @param pkg    the package to look filter out
     */
    private static void analyzeJar(Map<String, JarMergeInfo> swap, JarMergeInfo master, List<Pair<SecureJar, FabricModPath>> others, String pkg) {
        List<Path> additionalPaths = others.stream()
            .flatMap(pair -> {
                SecureJar sj = pair.getFirst();
                JarTransformer.FabricModFileMetadata metadata = pair.getSecond().metadata();
                SecureJar singlePackage = SecureJar.from(singlePackageFilter(pkg), sj.getPrimaryPath());
                JarMergeInfo jarInfo = swap.computeIfAbsent(sj.name(), name -> new JarMergeInfo(sj, metadata));
                jarInfo.excludedPackages().add(pkg);
                return Stream.of(singlePackage.getRootPath());
            })
            .toList();
        master.additionalPaths().addAll(additionalPaths);
    }

    /**
     * {@return a filter that only matches files in a single java package and in the root of the jar}
     *
     * @param pkg the package to match
     */
    private static BiPredicate<String, String> singlePackageFilter(String pkg) {
        return (path, basePath) -> {
            int idx = path.lastIndexOf('/');
            // Match any resource in the jar root
            return path.equals("/")
                // Match the package folder itself
                || idx == path.length() - 1
                // Match any file inside the package
                || idx > -1 && pkg.equals(path.substring(0, idx).replace('/', '.'));
        };
    }

    /**
     * Keeps track of pending package merging modifications that should be done to a jar.
     * @param jar the jar containing the package
     * @param metadata fabric mod metadata of the jar's mod
     * @param additionalPaths additional paths to include in the jar
     * @param excludedPackages packages to exlude from the jar
     */
    private record JarMergeInfo(SecureJar jar, JarTransformer.FabricModFileMetadata metadata, Set<Path> additionalPaths, Set<String> excludedPackages) {
        public JarMergeInfo(SecureJar jar, JarTransformer.FabricModFileMetadata metadata) {
            this(jar, metadata, new HashSet<>(), new HashSet<>());
        }
    }

    public record FilteredModPath(Path[] paths, @Nullable BiPredicate<String, String> filter, JarTransformer.FabricModFileMetadata metadata) {}
}
