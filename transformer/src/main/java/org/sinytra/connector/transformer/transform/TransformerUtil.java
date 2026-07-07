package org.sinytra.connector.transformer.transform;

import com.google.common.hash.Hashing;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TransformerUtil {
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final long ZIP_TIME = 318211200000L;
    public static final String METADATA_MARKER = "connector:active";
    public static final String LAUNCHPAD_MARKER = "launchpad:compatible";
    public static final String FLUID_TYPE_POLYFILL = "sinytra:use_default_fluid_type";

    // keywords, boolean and null literals, not allowed in identifiers
    // See jdk.internal.module.Checks#RESERVED
    private static final Set<String> RESERVED = Set.of(
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while",
        "true",
        "false",
        "null",
        "_"
    );

    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static boolean isJavaReservedKeyword(String str) {
        return RESERVED.contains(str);
    }

    public static CacheFile getCachedPath(@Nullable Path input, Path output, String cacheVersion) {
        return getCached(input != null ? rethrowSupplier(() -> Files.readAllBytes(input)) : null, output, cacheVersion);
    }

    public static CacheFile getCached(@Nullable Supplier<byte[]> input, Path output, String cacheVersion) {
        if (CACHE_ENABLED) {
            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try {
                String hash = cacheVersion;
                if (input != null) {
                    byte[] bytes = input.get();
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

    public static void cache(@Nullable Supplier<byte[]> input, Path output, ExceptionRunnable action, String cacheVersion) {
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

    public static void uncheck(ExceptionRunnable t) {
        try {
            t.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T uncheck(Callable<T> t) {
        try {
            return t.call();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> Consumer<T> rethrowConsumer(ExceptionConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T, R> Function<T, R> rethrowFunction(ExceptionFunction<T, R> func) {
        return t -> {
            try {
                return func.apply(t);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T> Supplier<T> rethrowSupplier(ExceptionSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
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

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Throwable;
    }

    @FunctionalInterface
    public interface ExceptionFunction<T, R> {
        R apply(T t) throws Throwable;
    }

    @FunctionalInterface
    public interface ExceptionConsumer<T> {
        void accept(T t) throws Throwable;
    }

    @FunctionalInterface
    public interface ExceptionSupplier<T> {
        T get() throws Throwable;
    }

    private TransformerUtil() {
    }
}
