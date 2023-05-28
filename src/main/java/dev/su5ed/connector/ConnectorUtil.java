package dev.su5ed.connector;

import cpw.mods.modlauncher.api.ServiceRunner;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConnectorUtil {
    public static final String MIXIN_CONFIGS_ATTRIBUTE = "ConnectorMixinConfigs";
    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static void cache(String version, Path input, Path output, ServiceRunner action) {
        try {
            if (!CACHE_ENABLED) {
                Files.deleteIfExists(output);
                action.run();
                return;
            }

            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try (InputStream is = Files.newInputStream(input)) {
                String checksum = version + "," + DigestUtils.sha256Hex(is);

                if (Files.exists(inputCache) && Files.exists(output)) {
                    String cached = Files.readString(inputCache);
                    if (cached.equals(checksum)) {
                        return;
                    }
                }

                Files.deleteIfExists(output);
                action.run();
                Files.writeString(inputCache, checksum);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private ConnectorUtil() {}
}
