package dev.su5ed.connector;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import org.slf4j.Logger;

import javax.swing.ListModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SplitPackageMerger {
    private static final Logger LOGGER = LogUtils.getLogger();

    // TODO Handle cases where the owner should have some packages excluded, too
    /*
    net.fabricmc.fabric.api.registry - fabric.commands.v0
    net.fabricmc.fabric.api.registry - fabric.content.registries.v0
    
    net.fabricmc.fabric.api.util     - fabric.content.registries.v0
    net.fabricmc.fabric.api.util     - fabric.api.base
     */

    public static List<Path> handleSplitPackages(List<Path> paths) {
        List<Path> singlePaths = new ArrayList<>(paths);

        // Package name -> list of containing jars
        Map<String, List<SecureJar>> allPkgs = new HashMap<>();
        for (Path path : singlePaths) {
            SecureJar secureJar = SecureJar.from(path);
            for (String pkg : secureJar.getPackages()) {
                allPkgs.computeIfAbsent(pkg, p -> new ArrayList<>()).add(secureJar);
            }
        }
        Map<String, List<SecureJar>> mergePkgs = new LinkedHashMap<>();
        AtomicInteger totalJars = new AtomicInteger(0);
        allPkgs.forEach((pkg, sources) -> {
            if (sources.size() > 1) {
                LOGGER.info("Found split package {} in jars {}", pkg, sources.stream().map(SecureJar::name).collect(Collectors.joining(",")));
                sources.forEach(source -> {
                    if (singlePaths.remove(source.getPrimaryPath())) {
                        totalJars.getAndIncrement();
                    }
                    mergePkgs.computeIfAbsent(pkg, p -> new ArrayList<>()).add(source);
                });
            }
        });
        LOGGER.info("Found {} split packages across {} jars", mergePkgs.keySet().size(), totalJars.get());

        Map<SecureJar, Set<String>> jarFilterMap = new HashMap<>();
        mergePkgs.forEach((pkg, sources) -> {
            String pkgPath = pkg.replace('.', '/') + '/';
            SecureJar owner = sources.get(0);
            Stream<Path> additionalPaths = sources.subList(1, sources.size()).stream()
                .map(sj -> {
                    SecureJar singlePackage = SecureJar.from((path, basePath) -> path.equals("/") || pkgPath.startsWith(path) || path.startsWith(pkgPath), sj.getPrimaryPath());
                    jarFilterMap.computeIfAbsent(sj, s -> new HashSet<>()).add(pkg);
                    return singlePackage.getRootPath();
                });
            SecureJar merged = SecureJar.from(Stream.concat(Stream.of(owner.getPrimaryPath()), additionalPaths).toArray(Path[]::new));
            singlePaths.add(merged.getRootPath());
        });
        // Handle non-owners
        jarFilterMap.forEach((sj, excludedPackages) -> {
            SecureJar filteredJar = SecureJar.from(new PackageTracker(Set.copyOf(excludedPackages)), sj.getPrimaryPath());
            singlePaths.add(filteredJar.getRootPath());
        });

        if (paths.size() != singlePaths.size()) {
            LOGGER.error("Expected {} paths, got {}", paths.size(), singlePaths.size());
            throw new IllegalStateException("Path size disprenancy detected!");
        }

        return singlePaths;
    }
}
