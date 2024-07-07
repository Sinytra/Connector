package org.sinytra.connector.locator;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.ClasspathLocatorUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.ModDirTransformerDiscoverer;
import net.neoforged.fml.loading.StringUtils;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;
import static net.neoforged.fml.loading.LogMarkers.SCAN;

public final class FabricModsDiscoverer {
    private static final String SUFFIX = ".jar";
    private static final String ADDITIONAL_MODS_PROPERTY = "connector.additionalModLocations";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Stream<Path> scanFabricMods() {
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();
        return Stream.of(scanModsDir(excluded), scanClasspath(), scanFromArguments(excluded))
            .flatMap(Function.identity());
    }

    private static Stream<Path> scanModsDir(List<Path> excluded) {
        return filterPaths(uncheck(() -> Files.list(FMLPaths.MODSDIR.get())), excluded);
    }

    private static Stream<Path> filterPaths(Stream<Path> stream, List<Path> excluded) {
        return stream
            .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
            .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
            .filter(FabricModsDiscoverer::isFabricModJar);
    }

    private static Stream<Path> scanClasspath() {
        if (FMLEnvironment.production) {
            return Stream.of();
        }
        try {
            List<Path> claimed = new ArrayList<>(Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator)).map(Path::of).toList());
            Stream.Builder<Path> ret = Stream.builder();
            Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(ConnectorUtil.FABRIC_MOD_JSON);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                Path path = ClasspathLocatorUtils.findJarPathFor(ConnectorUtil.FABRIC_MOD_JSON, ConnectorUtil.FABRIC_MOD_JSON, url);
                if (claimed.stream().noneMatch(path::equals) && Files.exists(path) && !Files.isDirectory(path) && isFabricModJar(path)) {
                    ret.add(path);
                }
            }
            return ret.build();
        } catch (IOException e) {
            LOGGER.error(SCAN, "Error trying to find resources", e);
            throw new RuntimeException(e);
        }
    }

    private static Stream<Path> scanFromArguments(List<Path> excluded) {
        final String[] paths = System.getProperty(ADDITIONAL_MODS_PROPERTY, "").split(",");
        if (paths.length == 0) {
            return Stream.of();
        }
        Stream.Builder<Path> files = Stream.builder();
        Arrays.stream(paths).filter(s -> !s.isBlank()).map(Path::of).forEach(path -> {
            if (Files.isDirectory(path)) {
                uncheck(() -> Files.list(path)).forEach(files::add);
            }
            else {
                files.add(path);
            }
        });
        return filterPaths(files.build(), excluded);
    }

    private static boolean isFabricModJar(Path path) {
        SecureJar secureJar = SecureJar.from(path);
        String name = secureJar.name();
        Path modsToml = secureJar.getPath(ConnectorUtil.MODS_TOML);
        if (Files.exists(modsToml) && !containsPlaceholder(modsToml)) {
            LOGGER.debug(SCAN, "Skipping jar {} as it contains a mods.toml file", path);
            return false;
        }
        if (secureJar.moduleDataProvider().findFile(ConnectorUtil.FABRIC_MOD_JSON).isPresent()) {
            LOGGER.debug(SCAN, "Found {} mod: {}", ConnectorUtil.FABRIC_MOD_JSON, path);
            return true;
        }
        LOGGER.info(SCAN, "Fabric mod metadata not found in jar {}, ignoring", name);
        return false;
    }

    private static boolean containsPlaceholder(Path modsTomlPath) {
        try {
            FileConfig fileConfig = FileConfig.of(modsTomlPath);
            fileConfig.load();
            fileConfig.close();
            NightConfigWrapper config = new NightConfigWrapper(fileConfig);
            return config.<Map<String, Object>>getConfigElement("properties")
                .map(map -> map.containsKey(ConnectorLocator.PLACEHOLDER_PROPERTY))
                .orElse(false);
        } catch (Throwable t) {
            LOGGER.error("Error reading placeholder information from {}", modsTomlPath, t);
            return false;
        }
    }
}
