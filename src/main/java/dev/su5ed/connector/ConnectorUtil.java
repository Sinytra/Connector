package dev.su5ed.connector;

import cpw.mods.modlauncher.api.ServiceRunner;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConnectorUtil {
    public static final boolean ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        ENABLED = prop == null || prop.equals("true");
    }

    public static void cache(String version, Path input, Path output, ServiceRunner action) {
        try {
            if (!ENABLED) {
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

                action.run();
                Files.writeString(inputCache, checksum);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private ConnectorUtil() {}
}
