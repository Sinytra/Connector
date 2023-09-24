package dev.su5ed.sinytra.connector.mod;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.slf4j.Logger;

public class ConnectorLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Whether we are currently in a loading state
    private static boolean loading;
    private static boolean finishedLoading;

    /**
     * @return whether we are currently in a loading state
     */
    public static boolean isLoading() {
        return loading;
    }

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
        if (ConnectorEarlyLoader.hasEncounteredException()) {
            LOGGER.error("Skipping early mod loading due to previous error");
            return;
        }

        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Loading mods", 0);
        try {
            loading = true;

            // Unfreeze registries
            unfreezeRegistries();

            // Invoke entry points
            EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                EntrypointUtils.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
            }
            else {
                EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
            }

            loading = false;
            finishedLoading = true;
        } catch (Throwable t) {
            throw ConnectorEarlyLoader.createGenericLoadingException(t, "Encountered error during early mod loading");
        }
        progress.complete();
    }

    @SuppressWarnings("deprecation")
    private static void unfreezeRegistries() {
        ((MappedRegistry<?>) BuiltInRegistries.REGISTRY).unfreeze();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ((MappedRegistry<?>) registry).unfreeze();
        }
    }
}
