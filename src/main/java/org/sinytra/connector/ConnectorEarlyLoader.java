package org.sinytra.connector;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.*;

public class ConnectorEarlyLoader {
    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODIDS = new HashSet<>();
    private static final List<IModInfo> CONNECTOR_MODS = new ArrayList<>();
    private static final List<Path> CONNECTOR_MOD_PATHS = new ArrayList<>();

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

    public static void addConnectorModPath(Path path) {
        CONNECTOR_MOD_PATHS.add(path);
    }

    public static boolean isConnectorMod(Path path) {
        return CONNECTOR_MOD_PATHS.contains(path);
    }

    public static void init() {
        LoadingModList list = FMLLoader.getCurrent().getLoadingModList();
        for (ModInfo mod : list.getMods()) {
            // FIXME Connector marker
            if (mod.getOwningFile().getFileProperties().containsKey("launchpad:active")) {
                CONNECTOR_MODIDS.add(mod.getModId());
                CONNECTOR_MODS.add(mod);
            }
        }
    }
}
