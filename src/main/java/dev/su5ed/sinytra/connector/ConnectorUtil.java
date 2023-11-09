package dev.su5ed.sinytra.connector;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import cpw.mods.modlauncher.api.ServiceRunner;
import dev.su5ed.sinytra.connector.locator.EmbeddedDependencies;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConnectorUtil {
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String MODS_TOML = "META-INF/mods.toml";
    public static final String CONNECTOR_MARKER = "connector_transformed";
    public static final String FORGE_MODID = "forge";
    public static final long ZIP_TIME = 318211200000L;
    public static final Path CONNECTOR_FOLDER = FMLPaths.MODSDIR.get().resolve(".connector");
    public static final String CONNECTOR_MODID = "connectormod";
    public static final String CONNECTOR_ISSUE_TRACKER_URL = "https://github.com/Sinytra/Connector/issues";
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
    private static final String MIXINEXTRAS_MODID = "com_github_llamalad7_mixinextras";
    private static final Version MIXINEXTRAS_ENTRYPOINT_VERSION = LamdbaExceptionUtils.uncheck(() -> Version.parse("0.2.0-beta.6"));
    // Never run entrypoints matching these values
    public static final Collection<String> DISABLED_MIXINEXTRAS_ENTRYPOINTS = Set.of(
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
    public static final Multimap<String, String> DEFAULT_GLOBAL_MOD_ALIASES = ImmutableMultimap.of(
        "cloth_config", "cloth-config2"
    );

    private static final boolean CACHE_ENABLED;

    static {
        String prop = System.getProperty("connector.cache.enabled");
        CACHE_ENABLED = prop == null || prop.equals("true");
    }

    public static CacheFile getCached(@Nullable Path input, Path output) {
        if (CACHE_ENABLED) {
            Path inputCache = output.getParent().resolve(output.getFileName() + ".input");
            try {
                String hash = EmbeddedDependencies.getJarCacheVersion();
                if (input != null) {
                    byte[] bytes = Files.readAllBytes(input);
                    hash += "," + Hashing.sha256().hashBytes(bytes);
                }

                if (Files.exists(inputCache)) {
                    if (Files.exists(output)) {
                        String cached = Files.readString(inputCache);
                        if (cached.equals(hash)) {
                            return new CacheFile(inputCache, hash, true);
                        }
                        else {
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

    public static void cache(@Nullable Path input, Path output, ServiceRunner action) {
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

    public static List<EntrypointMetadata> filterMixinExtrasEntrypoints(List<EntrypointMetadata> entrypoints) {
        return FabricLoader.getInstance().getModContainer(MIXINEXTRAS_MODID)
            .filter(mod -> mod.getMetadata().getVersion().compareTo(MIXINEXTRAS_ENTRYPOINT_VERSION) >= 0)
            .map(mod -> entrypoints.stream()
                .filter(metadata -> !DISABLED_MIXINEXTRAS_ENTRYPOINTS.contains(metadata.getValue()))
                .toList())
            .orElse(entrypoints);
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
