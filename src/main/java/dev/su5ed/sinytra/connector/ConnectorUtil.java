package dev.su5ed.sinytra.connector;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.modlauncher.api.ServiceRunner;
import dev.su5ed.sinytra.connector.locator.EmbeddedDependencies;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public final class ConnectorUtil {
    public static final String MIXIN_CONFIGS_ATTRIBUTE = "MixinConfigs";
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String MODS_TOML = "META-INF/mods.toml";
    public static final String CONNECTOR_MARKER = "connector_transformed";
    public static final long ZIP_TIME = 318211200000L;
    public static final Path CONNECTOR_FOLDER = FMLPaths.MODSDIR.get().resolve(".connector");
    // net.minecraft.util.StringUtil
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    // Ugly hardcoded values
    // Never load fabric mods of these mod ids
    public static final Collection<String> DISABLED_MODS = Set.of(
        // No matter what, we remove upstream fabric api from loading to prevent it from conflicting with FFAPI 
        // The unique mod filter isn't enough to handle api modules that have been left behind and not ported
        // I'm sorry for hardcoding this, but it seems to be the best way around
        "fabric_api"
    );
    // Never run entrypoints matching these values
    public static final Collection<String> DISABLED_ENTRYPOINTS = Set.of(
        // Mixinextras initializes itself from within its own mixin config plugin.
        // Attempting to initialize it from an entrypoint at the GAME layer will result in an "attempted duplicate class definition" error
        // It is redundant and no longer required, as mentioned in https://gist.github.com/LlamaLad7/ec597b6d02d39b8a2e35559f9fcce42f#initialization
        "com.llamalad7.mixinextras.MixinExtrasBootstrap",
        "com.llamalad7.mixinextras.MixinExtrasBootstrap::init"
    );
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
    // Common aliased mod dependencies that don't work with forge ports, which use a different modid.
    // They're too annoying to override individually in each mod, so we provide this small QoL feature for the user's comfort
    private static final BiMap<String, String> GLOBAL_DEPENDENCY_OVERRIDES = ImmutableBiMap.of(
        "cloth-config2", "cloth_config"
    );

    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static CacheFile getCached(Path input, Path output) {
        if (CACHE_ENABLED) {
            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try {
                byte[] bytes = Files.readAllBytes(input);
                String cacheVersion = EmbeddedDependencies.getJarCacheVersion();
                String checksum = cacheVersion + "," + Hashing.sha256().hashBytes(bytes);

                if (Files.exists(inputCache) && Files.exists(output)) {
                    String cached = Files.readString(inputCache);
                    if (cached.equals(checksum)) {
                        return new CacheFile(inputCache, checksum, true);
                    }
                    else {
                        Files.delete(output);
                        Files.delete(inputCache);
                    }
                }
                return new CacheFile(inputCache, checksum, false);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return new CacheFile(null, null, false);
    }

    public static void cache(Path input, Path output, ServiceRunner action) {
        CacheFile cacheFile = getCached(input, output);
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

    public static boolean isJavaReservedKeyword(String str) {
        return RESERVED.contains(str);
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

    public static String stripColor(String str) {
        return str != null ? STRIP_COLOR_PATTERN.matcher(str).replaceAll("") : null;
    }

    public static List<ModDependency> applyGlobalDependencyOverrides(Collection<ModDependency> dependencies) {
        return dependencies.stream()
            .map(dependency -> {
                for (Map.Entry<String, String> entry : GLOBAL_DEPENDENCY_OVERRIDES.entrySet()) {
                    String key = entry.getKey();
                    if (dependency.getKind() == ModDependency.Kind.DEPENDS && dependency.getModId().equals(key)) {
                        return uncheck(() -> new ModDependencyImpl(ModDependency.Kind.DEPENDS, entry.getValue(), List.of("*")));
                    }
                }
                return dependency;
            })
            .toList();
    }

    public static Map<String, String> getReverseAliases() {
        return GLOBAL_DEPENDENCY_OVERRIDES.inverse();
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

    private ConnectorUtil() {}
}
