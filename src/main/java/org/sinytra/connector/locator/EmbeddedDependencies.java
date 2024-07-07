package org.sinytra.connector.locator;

import com.google.common.base.Suppliers;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

/**
 * Handles locating and retrieving embedded jars that are shipped with Connector,
 * based on jar manifest attributes.
 */
public final class EmbeddedDependencies {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Fabric Loader upstream version included by Connector
    private static final String FABRIC_LOADER_VERSION = "Fabric-Loader-Version";

    private static final String ADAPTER_DATA_PATH = "adapter_data";
    public static final String ADAPTER_PATCH_DATA = "patch_data.json";
    public static final String ADAPTER_LVT_OFFSETS = "lvt_offsets.json";
    // Path to the jar this class is loaded from
    private static final Path SELF_PATH = uncheck(() -> {
        URL jarLocation = ConnectorLocator.class.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(jarLocation.toURI());
    });
    private static final Attributes ATTRIBUTES = readManifestAttributes();
    private static final Supplier<String> JAR_CACHE_VERSION = Suppliers.memoize(() -> {
        String ver = EmbeddedDependencies.class.getPackage().getImplementationVersion();
        if (ver == null) {
            LOGGER.error("Missing Connector jar version, disabling transformer caching");
            // Return a random string to still write an input file, so that once we have a proper version available we refresh the cache
            return RandomStringUtils.randomAlphabetic(5);
        }
        return ver + "," + FMLEnvironment.dist.name().toLowerCase();
    });

    public static Path getAdapterData(String path) {
        return SELF_PATH.resolve(ADAPTER_DATA_PATH).resolve(path);
    }

    @Nullable
    public static String getJarCacheVersion() {
        return JAR_CACHE_VERSION.get();
    }

    public static String getFabricLoaderVersion() {
        return ATTRIBUTES.getValue(FABRIC_LOADER_VERSION);
    }

    private static Attributes readManifestAttributes() {
        Path manifestPath = SELF_PATH.resolve(JarFile.MANIFEST_NAME);
        Manifest manifest;
        try (InputStream is = Files.newInputStream(manifestPath)) {
            manifest = new Manifest(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return manifest.getMainAttributes();
    }

    private EmbeddedDependencies() {}
}
