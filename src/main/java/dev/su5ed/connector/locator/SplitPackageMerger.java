package dev.su5ed.connector.locator;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
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

    public static List<Path> mergeSplitPackages(List<Path> paths) {
        List<Path> plainPaths = new ArrayList<>();
        List<Path> mergedPaths = new ArrayList<>();

        // Package name -> list of jars that contain the package
        Map<String, List<Pair<SecureJar, Path>>> pkgSources = new HashMap<>();
        for (Path path : paths) {
            SecureJar secureJar = SecureJar.from(path);
            for (String pkg : secureJar.getPackages()) {
                pkgSources.computeIfAbsent(pkg, p -> new ArrayList<>()).add(Pair.of(secureJar, path));
            }
            plainPaths.add(path);
        }

        // Find all jars that need merging
        List<SecureJar> jarOrder = new ArrayList<>();
        Map<String, List<SecureJar>> mergePkgs = new LinkedHashMap<>();
        AtomicInteger totalJars = new AtomicInteger(0);
        pkgSources.forEach((pkg, sources) -> {
            if (sources.size() > 1) {
                LOGGER.info(MERGER, "Found split package {} in jars {}", pkg, sources.stream().map(info -> info.getFirst().name()).collect(Collectors.joining(",")));
                sources.forEach(source -> {
                    SecureJar sj = source.getFirst();
                    if (plainPaths.remove(source.getSecond())) {
                        totalJars.getAndIncrement();
                    }
                    mergePkgs.computeIfAbsent(pkg, p -> new ArrayList<>()).add(sj);
                    jarOrder.add(sj);
                });
            }
        });
        LOGGER.info(MERGER, "Found {} split packages across {} jars", mergePkgs.keySet().size(), totalJars.get());

        // Name -> Jar filter info
        Map<String, JarMergeInfo> jarMap = new HashMap<>();
        mergePkgs.forEach((pkg, sources) -> {
            sources.sort(Comparator.comparingInt(jarOrder::indexOf));

            SecureJar candidate = sources.get(0);
            String name = candidate.name();
            JarMergeInfo owner = jarMap.computeIfAbsent(name, str -> new JarMergeInfo(candidate));
            analyzeJar(jarMap, owner, sources.subList(1, sources.size()), pkg);
        });

        jarMap.values().forEach(info -> {
            Set<Path> additionalPaths = info.additionalPaths();
            Set<String> excludedPackages = info.excludedPackages();
            BiPredicate<String, String> filter = !excludedPackages.isEmpty() ? new PackageTracker(Set.copyOf(excludedPackages)) : null;
            SecureJar merged = SecureJar.from(filter, Stream.concat(Stream.of(info.jar().getPrimaryPath()), additionalPaths.stream()).toArray(Path[]::new));
            mergedPaths.add(merged.getRootPath());
        });

        mergedPaths.addAll(plainPaths);
        if (paths.size() != mergedPaths.size()) {
            LOGGER.error("Expected {} paths, got {}", paths.size(), plainPaths.size());
            throw new IllegalStateException("Path size disprenancy detected!");
        }

        return mergedPaths;
    }

    private static void analyzeJar(Map<String, JarMergeInfo> swap, JarMergeInfo master, List<SecureJar> others, String pkg) {
        List<Path> additionalPaths = others.stream()
            .flatMap(sj -> {
                SecureJar singlePackage = SecureJar.from(singlePackageFilter(pkg), sj.getPrimaryPath());
                JarMergeInfo jarInfo = swap.computeIfAbsent(sj.name(), name -> new JarMergeInfo(sj));
                jarInfo.excludedPackages().add(pkg);
                return Stream.of(singlePackage.getRootPath());
            })
            .toList();
        master.additionalPaths().addAll(additionalPaths);
    }

    private static BiPredicate<String, String> singlePackageFilter(String pkg) {
        return (path, basePath) -> {
            int idx = path.lastIndexOf('/');
            return path.equals("/") || idx == path.length() - 1 || idx > -1 && pkg.equals(path.substring(0, idx).replace('/', '.'));
        };
    }

    private record JarMergeInfo(SecureJar jar, Set<Path> additionalPaths, Set<String> excludedPackages) {
        public JarMergeInfo(SecureJar jar) {
            this(jar, new HashSet<>(), new HashSet<>());
        }
    }
}
