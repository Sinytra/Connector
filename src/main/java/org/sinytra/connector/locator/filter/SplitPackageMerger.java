package org.sinytra.connector.locator.filter;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarContents.FilteredPath;
import net.neoforged.fml.jarcontents.JarContents.PathFilter;
import net.neoforged.fml.jarmoduleinfo.JarModuleInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.jar.FabricModFileMetadata;
import org.sinytra.connector.transformer.jar.JarTransformer.FabricModPath;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.sinytra.connector.transformer.transform.TransformerUtil.uncheck;

public class SplitPackageMerger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PathFilter BASE_FILTER = path -> !path.startsWith("META-INF/versions");

    /**
     * Detect and resolve split package conflicts in jars.
     * Supplied paths must point to valid jars paths usable by {@link JarContents}.
     * @param paths jar paths to process
     * @return a list of adjusted jar paths
     */
    public static List<FilteredPaths> mergeSplitPackages(List<FabricModPath> paths, Iterable<IModFile> existing, Collection<? super IModFile> ignoredModFiles) {
        // Paths that don't contain conflicting jars
        List<FabricModPath> plainPaths = new ArrayList<>(paths);
        // Processed paths result
        List<FilteredPaths> output = new ArrayList<>();

        // Package name -> list of jars that contain the package
        Map<String, List<Pair<JarContents, FabricModPath>>> pkgSources = new HashMap<>();
        for (FabricModPath modInfo : paths) {
            JarContents jar = uncheck(() -> JarContents.ofPath(modInfo.path()));
            Collection<String> packages = JarModuleInfo.scanModulePackages(jar);
            for (String pkg : packages) {
                pkgSources.computeIfAbsent(pkg, _ -> new ArrayList<>())
                    .add(Pair.of(jar, modInfo));
            }
        }

        // Find all jars that need merging
        // Keep track of the order jars were found in, for selecting package owners
        List<JarContents> jarOrder = new ArrayList<>();
        // Map of packages that need merging and their sources
        Map<String, List<Pair<JarContents, FabricModPath>>> mergePkgs = new LinkedHashMap<>();
        AtomicInteger totalJars = new AtomicInteger(0);
        pkgSources.forEach((pkg, sources) -> {
            if (sources.size() > 1) {
                String names = sources.stream()
                    .map(info -> info.getFirst().getPrimaryPath().getFileName().toString())
                    .collect(Collectors.joining(","));
                LOGGER.debug("Found split package {} in jars {}", pkg, names);

                sources.forEach(source -> {
                    if (plainPaths.remove(source.getSecond())) {
                        totalJars.getAndIncrement();
                    }
                    mergePkgs.computeIfAbsent(pkg, _ -> new ArrayList<>()).add(source);
                    jarOrder.add(source.getFirst());
                });
            }
        });
        LOGGER.debug("Found {} split packages across {} jars", mergePkgs.size(), totalJars.get());

        // Name -> Jar merge info
        Map<Object, JarMergeInfo> jarMap = new HashMap<>();
        mergePkgs.forEach((pkg, sources) -> {
            // Sort sources in the order jars were discovered
            sources.sort(Comparator.comparingInt(p -> jarOrder.indexOf(p.getFirst())));

            Pair<JarContents, FabricModPath> pair = sources.getFirst();
            JarContents candidate = pair.getFirst();
            JarMergeInfo owner = jarMap.computeIfAbsent(
                getJarKey(candidate),
                _ -> new JarMergeInfo(candidate.getPrimaryPath(), pair.getSecond().metadata())
            );
            analyzeJar(jarMap, owner, sources.subList(1, sources.size()), pkg);
        });

        // Find packages that are already loaded, along with packages of discovered mods
        Set<String> existingPackages = new HashSet<>();
        for (IModFile modFile : existing) {
            if (!ignoredModFiles.contains(modFile)) {
                Set<String> packages = JarModuleInfo.scanModulePackages(modFile.getContents());
                existingPackages.addAll(packages);
            }
        }

        // Remove existing classpath packages
        for (String pkg : existingPackages) {
            List<Pair<JarContents, FabricModPath>> list = pkgSources.get(pkg);
            if (list != null) {
                for (Pair<JarContents, FabricModPath> pair : list) {
                    JarContents jar = pair.getFirst();
                    FabricModPath modPath = pair.getSecond();
                    plainPaths.remove(modPath);
                    JarMergeInfo info = jarMap.computeIfAbsent(
                        getJarKey(jar),
                        _ -> new JarMergeInfo(jar.getPrimaryPath(), modPath.metadata())
                    );
                    LOGGER.debug("Excluding existing package {} from jar {}", pkg, jar.getPrimaryPath());
                    info.excludedPackages().add(pkg);
                }
            }
        }

        // Process gathered merge information
        jarMap.values().forEach(info -> {
            Set<FilteredPath> additionalPaths = info.additionalPaths();
            Set<String> excludedPackages = info.excludedPackages();

            PathFilter filter = !excludedPackages.isEmpty() ? new PackageTracker(Set.copyOf(excludedPackages)) : null;
            Collection<FilteredPath> jarPaths = Stream.concat(
                Stream.of(new FilteredPath(info.origin(), mergeANDFilter(BASE_FILTER, filter))),
                additionalPaths.stream()
            ).toList();
            output.add(new FilteredPaths(jarPaths));
        });

        // Add unprocessed paths to output
        for (FabricModPath modPath : plainPaths) {
            FilteredPath path = new FilteredPath(modPath.path(), BASE_FILTER);
            output.add(new FilteredPaths(List.of(path)));
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
    private static void analyzeJar(Map<Object, JarMergeInfo> swap, JarMergeInfo master, List<Pair<JarContents, FabricModPath>> others, String pkg) {
        List<FilteredPath> additionalPaths = others.stream()
            .map(pair -> {
                JarContents jar = pair.getFirst();
                FabricModFileMetadata metadata = pair.getSecond().metadata();

                FilteredPath filteredPath = new FilteredPath(jar.getPrimaryPath(), singlePackageFilter(pkg));
                JarMergeInfo jarInfo = swap.computeIfAbsent(getJarKey(jar), name -> new JarMergeInfo(jar.getPrimaryPath(), metadata));
                jarInfo.excludedPackages().add(pkg);

                return filteredPath;
            })
            .toList();
        master.additionalPaths().addAll(additionalPaths);
    }

    /**
     * {@return a filter that only matches files in a single java package and in the root of the jar}
     *
     * @param pkg the package to match
     */
    private static PathFilter singlePackageFilter(String pkg) {
        return path -> {
            int idx = path.lastIndexOf('/');
            // Match any resource in the jar root
            return path.equals("/")
                // Match the package folder itself
                || idx == path.length() - 1
                // Match any file inside the package
                || idx > -1 && pkg.equals(path.substring(0, idx).replace('/', '.'));
        };
    }

    private static PathFilter mergeANDFilter(PathFilter left, @Nullable PathFilter right) {
        return a -> left.test(a) && (right == null || right.test(a));
    }
    
    private static Object getJarKey(JarContents contents) {
        if (contents.getContentRoots().size() == 1) {
            return contents.getPrimaryPath();
        }
        return contents.getChecksum().orElseThrow(() -> new IllegalStateException("Cannot calculate jar key"));
    }

    /**
     * Keeps track of pending package merging modifications that should be done to a jar.
     * @param origin the origin primary path
     * @param metadata fabric mod metadata of the jar's mod
     * @param additionalPaths additional paths to include in the jar
     * @param excludedPackages packages to exlude from the jar
     */
    private record JarMergeInfo(Path origin, FabricModFileMetadata metadata, Set<FilteredPath> additionalPaths, Set<String> excludedPackages) {
        public JarMergeInfo(Path origin, FabricModFileMetadata metadata) {
            this(origin, metadata, new HashSet<>(), new HashSet<>());
        }
    }

    public record FilteredPaths(Collection<FilteredPath> paths) {}
}
