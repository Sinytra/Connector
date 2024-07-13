package org.sinytra.connector.mod;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.mod.compat.LazyEntityAttributes;
import org.sinytra.connector.mod.mixin.registries.NeoForgeRegistriesSetupAccessor;
import org.slf4j.Logger;

public class ConnectorLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Whether we are currently in a loading state
    private static boolean finishedLoading;

    public static boolean hasFinishedLoading() {
        return finishedLoading;
    }

    /**
     * Invoke the main entrypoints for located connector mods. Any exceptions thrown by mods are ignored and thrown
     * later during FML load.
     *
     * @see ConnectorEarlyLoader#CONNECTOR_MODS
     */
    public static void load() {
        NeoForgeRegistriesSetupAccessor.invokeModifyRegistries(null);

        if (ConnectorEarlyLoader.hasEncounteredException()) {
            LOGGER.error("Skipping early mod loading due to previous error");
            return;
        }

        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Loading mods", 0);
        try {
            LazyEntityAttributes.inject();

            // Invoke entry points
            FabricLoader loader = FabricLoader.getInstance();
            loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                loader.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
            }
            else {
                loader.invokeEntrypoints("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
            }

            LazyEntityAttributes.release();
            finishedLoading = true;
        } catch (Throwable t) {
            ConnectorEarlyLoader.addGenericLoadingException(t, "Encountered error during early mod loading");
        }
        progress.complete();
    }
}
