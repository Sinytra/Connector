package dev.su5ed.sinytra.connector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.modlauncher.api.ServiceRunner;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConnectorUtil {
    public static final String MIXIN_CONFIGS_ATTRIBUTE = "ConnectorMixinConfigs";
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String CONNECTOR_LANGUAGE = "connector";
    public static final long ZIP_TIME = 318211200000L;
    public static final Path CONNECTOR_FOLDER = FMLPaths.MODSDIR.get().resolve(".connector");

    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static CacheFile getCached(String version, Path input, Path output) {
        if (CACHE_ENABLED) {
            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try (InputStream is = Files.newInputStream(input)) {
                String checksum = version + "," + DigestUtils.sha256Hex(is);

                if (Files.exists(inputCache) && Files.exists(output)) {
                    String cached = Files.readString(inputCache);
                    if (cached.equals(checksum)) {
                        return new CacheFile(inputCache, checksum, true);
                    }
                }
                return new CacheFile(inputCache, checksum, false);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return new CacheFile(null, null, false);
    }

    public static void cache(String version, Path input, Path output, ServiceRunner action) {
        CacheFile cacheFile = getCached(version, input, output);
        if (!cacheFile.isUpToDate()) {
            try {
                Files.deleteIfExists(output);
                action.run();
                cacheFile.save();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }   
        }
    }

    public static Gson prettyGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    public static <V> V uncheckThrowable(UncheckedSupplier<V> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @FunctionalInterface
    public interface UncheckedSupplier<V> {
        V get() throws Throwable;
    }

    public static class CacheFile {
        private final Path inputCache;
        private final String inputChecksum;
        private boolean isUpToDate;

        public CacheFile(Path inputCache, String inputChecksum, boolean isUpToDate) {
            this.inputCache = inputCache;
            this.inputChecksum = inputChecksum;
            this.isUpToDate = isUpToDate;
        }

        public boolean isUpToDate() {
            return this.isUpToDate;
        }

        public void save() {
            try {
                Files.writeString(this.inputCache, this.inputChecksum);
                this.isUpToDate = true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private ConnectorUtil() {}
}
