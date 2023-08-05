package dev.su5ed.sinytra.connector.mod;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorExceptionHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODS = new HashSet<>();
    // Whether we are currently in a loading state
    private static boolean loading;

    /**
     * @return whether we are currently in a loading state
     */
    public static boolean isLoading() {
        return loading;
    }

    /**
     * @param modid the mod id to look up
     * @return whether a mod with the given modid is loaded via Connector
     */
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODS.contains(modid);
    }

    /**
     * Run initial fabric loader setup and invoke preLaunch entrypoint. Any exceptions thrown are ignored and thrown
     * later during FML load.
     *
     * @see #CONNECTOR_MODS
     * @see ConnectorExceptionHandler#loadingException
     */
    @SuppressWarnings("unused")
    public static void setup() {
        if (ConnectorExceptionHandler.getLoadingException() != null) {
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
            ConnectorExceptionHandler.addSuppressed(t);
        }
        progress.complete();
    }

    /**
     * Invoke the main entrypoints for located connector mods. Any exceptions thrown by mods are ignored and thrown
     * later during FML load.
     *
     * @see #CONNECTOR_MODS
     * @see ConnectorExceptionHandler#loadingException
     */
    public static void load() {
        if (ConnectorExceptionHandler.getLoadingException() != null) {
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

            // Freeze registries again
            freezeRegistries();

            loading = false;
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod loading", t);
            ConnectorExceptionHandler.addSuppressed(t);
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

    private static void freezeRegistries() {
        BuiltInRegistries.REGISTRY.freeze();
        BuiltInRegistries.REGISTRY.forEach(Registry::freeze);
    }
}
