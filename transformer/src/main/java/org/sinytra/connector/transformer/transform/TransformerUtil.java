package org.sinytra.connector.transformer.transform;

import com.google.common.hash.Hashing;
import cpw.mods.modlauncher.api.ServiceRunner;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public final class TransformerUtil {
    public static final Unsafe UNSAFE = uncheck(() -> {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    });
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String AT_PATH = "META-INF/accesstransformer.cfg";
    public static final long ZIP_TIME = 318211200000L;

    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static CacheFile getCached(@Nullable Path input, Path output, String cacheVersion) {
        if (CACHE_ENABLED) {
            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try {
                String hash = cacheVersion;
                if (input != null) {
                    byte[] bytes = Files.readAllBytes(input);
                    hash += "," + Hashing.sha256().hashBytes(bytes);
                }

                if (Files.exists(inputCache)) {
                    if (Files.exists(output)) {
                        String cached = Files.readString(inputCache);
                        if (cached.equals(hash)) {
                            return new CacheFile(inputCache, hash, true);
                        } else {
                            Files.delete(output);
                            Files.delete(inputCache);
                        }
                    }
                } else {
                    Files.deleteIfExists(output);
                }
                return new CacheFile(inputCache, hash, false);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return new CacheFile(null, null, false);
    }

    public static void cache(@Nullable Path input, Path output, ServiceRunner action, String cacheVersion) {
        CacheFile cacheFile = getCached(input, output, cacheVersion);
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

    @SuppressWarnings("unchecked")
    public static <U> U allocateInstance(Class<U> clazz) throws InstantiationException {
        return (U) UNSAFE.allocateInstance(clazz);
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
            if (this.inputCache != null) {
                try {
                    Files.writeString(this.inputCache, this.inputChecksum);
                    this.isUpToDate = true;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private TransformerUtil() {
    }
}
