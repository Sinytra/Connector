package dev.su5ed.sinytra.connector.loader;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODS = new HashSet<>();
    // If we encounter an exception during setup/load, we store it here and throw it later during FML mod loading,
    // so that it is propagated to the forge error screen.
    private static Throwable loadingException;

    /**
     * @param modid the mod id to look up
     * @return whether a mod with the given modid is loaded via Connector
     */
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODS.contains(modid);
    }

    /**
     * @return a suppressed exception if one was encountered during setup/load, otherwise {@code null}
     */
    @Nullable
    public static Throwable getLoadingException() {
        return loadingException;
    }

    public static void addSuppressed(Throwable t) {
        if (loadingException == null) {
            loadingException = t;
        }
        else {
            loadingException.addSuppressed(t);
        }
    }

    /**
     * Run initial fabric loader setup and invoke preLaunch entrypoint. Any exceptions thrown are ignored and thrown
     * later during FML load.
     *
     * @see #CONNECTOR_MODS
     * @see #loadingException
     */
    @SuppressWarnings("unused")
    public static void setup() {
        if (getLoadingException() != null) {
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
                    CONNECTOR_MODS.add(mod.getModId());
                }
            }
            // Propagate mods to fabric
            FabricLoaderImpl.INSTANCE.addFmlMods(mods);
            // Setup fabric loader state
            FabricLoaderImpl.INSTANCE.setup();
            // Invoke prelaunch entrypoint
            EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod setup", t);
            addSuppressed(t);
        }
        progress.complete();
    }
}
