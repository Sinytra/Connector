package dev.su5ed.sinytra.connector.loader;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    // A list of modids that use the connector language provider
    private static final Set<String> CONNECTOR_MODS = new HashSet<>();
    // Whether we are currently in a loading state
    private static boolean loading;
    // If we encounter an exception during setup/load, we store it here and throw it later during FML mod loading,
    // so that it is propagated to the forge error screen.
    private static Throwable loadingException;

    /**
     * Get the constructed instances for a connector mod by its id
     *
     * @param modid the mod id to look up
     * @return the mod's instances, if any exist
     */
    public static Collection<Object> getModInstances(String modid) {
        Collection<Object> instances = FabricLoaderImpl.INSTANCE.getModInstances(modid);
        return instances == null ? List.of() : instances;
    }

    /**
     * @return whether we are currently in a loading state
     */
    public static boolean isLoading() {
        return loading;
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
     * @param modid the mod id to look up
     * @return whether a mod with the given modid uses the connector language provider
     */
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODS.contains(modid);
    }

    /**
     * Run initial fabric loader setup and invoke preLaunch entrypoint. Any exceptions thrown are ignored and thrown
     * later during FML load.
     *
     * @see #CONNECTOR_MODS
     * @see #loadingException
     */
    public static void setup() {
        if (loadingException != null) {
            LOGGER.error("Skipping early mod setup due to previous error");
            return;
        }

        LOGGER.debug("Starting early connector loader setup");
        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Early Setup", 0);
        try {
            // Find all connector loader mods
            List<ModInfo> mods = LoadingModList.get().getMods();
            for (ModInfo mod : mods) {
                if (mod.getOwningFile().requiredLanguageLoaders().stream().anyMatch(spec -> spec.languageName().equals(ConnectorUtil.CONNECTOR_LANGUAGE))) {
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

    /**
     * Invoke the main entrypoints for located connector mods. Any exceptions thrown by mods are ignored and thrown
     * later during FML load.
     *
     * @see #CONNECTOR_MODS
     * @see #loadingException
     */
    public static void load() {
        if (loadingException != null) {
            LOGGER.error("Skipping early mod loading due to previous error");
            return;
        }

        ProgressMeter progress = StartupNotificationManager.addProgressBar("[Connector] Loading mods", 0);
        try {
            loading = true;

            // Create RegistryHelper service to act as a bridge to the GAME layer
            ModuleLayer gameLayer = Launcher.INSTANCE.findLayerManager().flatMap(manager -> manager.getLayer(IModuleLayerManager.Layer.GAME)).orElseThrow();
            RegistryHelper registryHelper = ServiceLoader.load(gameLayer, RegistryHelper.class).stream().findFirst().orElseThrow().get();

            // Unfreeze registries
            registryHelper.unfreezeRegistries();

            // Invoke entry points
            EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                EntrypointUtils.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
            } else {
                EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
            }

            // Freeze registries again
            registryHelper.freezeRegistries();

            loading = false;
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod loading", t);
            addSuppressed(t);
        }
        progress.complete();
    }
}
