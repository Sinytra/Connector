package org.sinytra.connector;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODIDS = new HashSet<>();
    private static final List<IModInfo> CONNECTOR_MODS = new ArrayList<>();
    private static final List<Path> CONNECTOR_MOD_PATHS = new ArrayList<>();
    // If we encounter an exception during setup/load, we store it here and throw it later during FML mod loading,
    // so that it is propagated to the forge error screen.
    private static final List<ModLoadingIssue> LOADING_EXCEPTIONS = new ArrayList<>();

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

    /**
     * @return Suppressed exceptions that were encountered during setup/load
     */
    public static List<ModLoadingIssue> getLoadingExceptions() {
        return LOADING_EXCEPTIONS;
    }

    /**
     * @return Whether a loading exception has been encountered up to this point in loading
     */
    public static boolean hasEncounteredException() {
        LoadingModList list = FMLLoader.getCurrent().getLoadingModList();
        return list != null && list.hasErrors();
    }

    public static void addGenericLoadingException(Throwable t, String message) {
        addGenericLoadingException(createGenericLoadingIssue(t, message));
    }

    public static void addGenericLoadingException(ModLoadingIssue issue) {
        addGenericLoadingException(List.of(issue));
    }

    public static void addGenericLoadingException(List<ModLoadingIssue> issues) {
        LOADING_EXCEPTIONS.addAll(issues);
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
        if (hasEncounteredException()) {
            LOGGER.error("Skipping early mod setup due to previous error");
            return;
        }

        LoadingModList list = FMLLoader.getCurrent().getLoadingModList();
        for (ModInfo mod : list.getMods()) {
            if (mod.getOwningFile().getFileProperties().containsKey(TransformerUtil.METADATA_MARKER)) {
                CONNECTOR_MODIDS.add(mod.getModId());
                CONNECTOR_MODS.add(mod);
            }
        }
    }
}
