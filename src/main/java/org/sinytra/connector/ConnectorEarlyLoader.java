package org.sinytra.connector;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.sinytra.connector.transformer.jar.JarTransformer.TransformedFabricModPath;
import org.sinytra.connector.transformer.transform.TransformerUtil.CacheFile;

import java.util.*;

public class ConnectorEarlyLoader {
    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODIDS = new HashSet<>();
    private static final List<IModInfo> CONNECTOR_MODS = new ArrayList<>();
    private static final List<CacheFile> PENDING_CACHE = new ArrayList<>();

    /**
     * @param modid the mod id to look up
     * @return whether a mod with the given modid is loaded via Connector
     */
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODIDS.contains(modid);
    }

    public static boolean isConnectorModClass(Class<?> cls) {
        return cls.getModule().isNamed() && isConnectorMod(cls.getModule().getName());
    }

    public static List<IModInfo> getConnectorMods() {
        return CONNECTOR_MODS;
    }

    public static ModLoadingIssue createGenericLoadingIssue(Throwable original, String message) {
        return createLoadingIssue(original, "§e[Connector]§r {0}\n§c{1}§7: {2}§r", true, message, original.getClass().getName(), original.getMessage());
    }

    public static ModLoadingIssue createLoadingIssue(Throwable original, String message, boolean keepOriginal, Object... args) {
        return new ModLoadingIssue(ModLoadingIssue.Severity.ERROR, message, Arrays.asList(args), keepOriginal ? original : null, null, null, null);
    }

    public static void init(List<IModFile> mods, List<TransformedFabricModPath> output) {
        for (IModFile file : mods) {
            if (file.getModInfos().size() != 1) {
                throw new RuntimeException("Expected to find a single mod");
            }

            IModInfo mod = file.getModFileInfo().getMods().getFirst();

            CONNECTOR_MODIDS.add(mod.getModId());
            CONNECTOR_MODS.add(mod);
        }

        for (TransformedFabricModPath path : output) {
            if (path.needsUpdate()) {
                PENDING_CACHE.add(path.cacheFile());
            }
        }
    }

    public static void finalizeCache() {
        for (CacheFile file : PENDING_CACHE) {
            file.save();
        }
        PENDING_CACHE.clear();
    }
}
