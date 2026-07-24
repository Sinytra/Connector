package org.sinytra.connector.util;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile.Type;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.sinytra.launchpad.api.FabricModFactory;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

public final class ConnectorUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Unsafe UNSAFE = TransformerUtil.uncheck(() -> {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    });
    @SuppressWarnings("removal")
    public static final MethodHandles.Lookup TRUSTED_LOOKUP = TransformerUtil.uncheck(() -> {
        Field hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        return (MethodHandles.Lookup) ConnectorUtil.UNSAFE.getObject(ConnectorUtil.UNSAFE.staticFieldBase(hackfield), ConnectorUtil.UNSAFE.staticFieldOffset(hackfield));
    });

    private static final Supplier<String> JAR_CACHE_VERSION = Suppliers.memoize(() -> {
        if (!FMLLoader.getCurrent().isProduction()) {
            return "__dev__";
        }
        String ver = ConnectorUtil.class.getPackage().getImplementationVersion();
        if (ver == null) {
            LOGGER.error("Missing Connector jar version, disabling transformer caching");
            // Return a random string to still write an input file, so that once we have a proper version available we refresh the cache
            return RandomStringUtils.secure().nextAlphabetic(5);
        }
        return ver + "," + FMLEnvironment.getDist().name().toLowerCase();
    });

    public static final Supplier<Boolean> SHOULD_ENABLE = Suppliers.memoize(() -> {
        try {
            Class.forName("org.sinytra.launchpad.api.Launchpad");
            FabricModFactory.createModFile(JarContents.empty(Path.of("nonexistent")), ModFileDiscoveryAttributes.DEFAULT, Type.MOD);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodError e) {
            return false;
        }
    });

    // Ugly hardcoded values
    // Never load fabric mods of these mod ids
    public static final Collection<String> DISABLED_MODS = Set.of(
        // No matter what, we remove upstream fabric api from loading to prevent it from conflicting with FFAPI 
        // The unique mod filter isn't enough to handle api modules that have been left behind and not ported
        // I'm sorry for hardcoding this, but it seems to be the best way around
        "fabric-api", "fabric_api",
        // Mixinextras is included by NeoForge
        "mixinextras"
    );

    // Common aliased mod dependencies that don't work with forge ports, which use a different modid.
    // They're too annoying to override individually in each mod, so we provide this small QoL feature for the user's comfort
    public static final Multimap<String, String> DEFAULT_GLOBAL_MOD_ALIASES = ImmutableMultimap.of(
        "cloth_config", "cloth-config2",
        "playeranimator", "player-animator"
    );

    // Provided by forgified-fabric-loader
    public static final String FABRIC_METADATA = "fabric:metadata";

    @Nullable
    public static String getJarCacheVersion() {
        return JAR_CACHE_VERSION.get();
    }

    private ConnectorUtil() {
    }
}
