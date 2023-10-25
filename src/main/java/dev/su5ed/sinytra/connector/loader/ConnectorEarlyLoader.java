package dev.su5ed.sinytra.connector.loader;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.minecraftforge.fml.loading.EarlyLoadingException;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODIDS = new HashSet<>();
    private static final List<IModInfo> CONNECTOR_MODS = new ArrayList<>();
    // If we encounter an exception during setup/load, we store it here and throw it later during FML mod loading,
    // so that it is propagated to the forge error screen.
    private static final List<EarlyLoadingException> LOADING_EXCEPTIONS = new ArrayList<>();

    /**
     * @param modid the mod id to look up
     * @return whether a mod with the given modid is loaded via Connector
     */
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODIDS.contains(modid);
    }

    public static List<IModInfo> getConnectorMods() {
        return CONNECTOR_MODS;
    }

    /**
     * @return Suppressed exceptions that were encountered during setup/load
     */
    public static List<EarlyLoadingException> getLoadingExceptions() {
        return LOADING_EXCEPTIONS;
    }

    /**
     * @return Whether a loading exception has been encountered up to this point in loading
     */
    public static boolean hasEncounteredException() {
        return !LOADING_EXCEPTIONS.isEmpty() || LoadingModList.get() != null && !LoadingModList.get().getErrors().isEmpty();
    }

    /**
     * Nicely wraps the exception message in a color coded format for easier readability.
     * 
     * @param t the encountered exception
     * @param message simple error message to show
     */
    public static void addGenericLoadingException(Throwable t, String message) {
        EarlyLoadingException exception = createGenericLoadingException(t, message);
        if (LoadingModList.get() != null) {
            LoadingModList.get().getErrors().add(exception);
        } else {
            LOADING_EXCEPTIONS.add(exception);
        }
    }

    public static EarlyLoadingException createGenericLoadingException(Throwable original, String message) {
        return createLoadingException(original, "§e[Connector]§r {3}\n§c{4}§7: {5}§r", message, original.getClass().getName(), original.getMessage());
    }

    public static EarlyLoadingException createLoadingException(Throwable original, String message, Object... args) {
        return new EarlyLoadingException(ConnectorUtil.stripColor(original.getMessage()), original, List.of(new EarlyLoadingException.ExceptionData(message, args)));
    }

    /**
     * Run initial fabric loader setup. Any exceptions thrown are ignored and re-thrown later during FML load.
     *
     * @see #CONNECTOR_MODIDS
     */
    @SuppressWarnings("unused")
    public static void init() {
        if (hasEncounteredException()) {
            LOGGER.error("Skipping early mod setup due to previous error");
            return;
        }

        LOGGER.debug("Starting early connector loader setup");
        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Early Setup", 0);
        try {
            // Find all connector loader mods
            List<ModInfo> mods = LoadingModList.get().getMods();
            for (ModInfo mod : mods) {
                if (mod.getOwningFile().getFileProperties().containsKey(ConnectorUtil.CONNECTOR_MARKER)) {
                    CONNECTOR_MODIDS.add(mod.getModId());
                    CONNECTOR_MODS.add(mod);
                }
            }
            // Propagate mods to fabric
            FabricLoaderImpl.INSTANCE.addFmlMods(mods);
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod setup", t);
            addGenericLoadingException(t, "Encountered an error during early mod setup");
        }
        progress.complete();
    }

    public static void setup() {
        try {
            // Setup fabric loader
            FabricLoaderImpl.INSTANCE.setup();
        } catch (Throwable t) {
            LOGGER.error("Encountered an error during fabric loader setup", t);
            addGenericLoadingException(t, "Encountered an error during fabric loader setup");
        }
    }

    public static void preLaunch() {
        LOGGER.debug("Running prelaunch entrypoint");
        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] PreLaunch", 0);
        try {
            // Invoke prelaunch entrypoint
            EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
        } catch (Throwable t) {
            LOGGER.error("Encountered an error in prelaunch entrypoint", t);
            addGenericLoadingException(t, "Encountered an error in prelaunch entrypoint");
        }
        progress.complete();
    }
}
