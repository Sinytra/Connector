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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> CONNECTOR_MODS = new HashSet<>();
    private static boolean loading;
    private static Throwable loadingException;

    public static Collection<Object> getModInstances(String modid) {
        Collection<Object> instances = FabricLoaderImpl.INSTANCE.getModInstances(modid);
        return instances == null ? List.of() : instances;
    }

    public static boolean isLoading() {
        return loading;
    }

    @Nullable
    public static Throwable getLoadingException() {
        return loadingException;
    }
    
    public static boolean isConnectorMod(String modid) {
        return CONNECTOR_MODS.contains(modid);
    }

    public static void setup() {
        LOGGER.debug("ConnectorEarlyLoader starting");
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
            FabricLoaderImpl.INSTANCE.setup();
            // Call prelaunch entrypoints
            EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod setup", t);
            loadingException = t;
        }
    }

    public static void load() {
        if (loadingException != null) {
            LOGGER.error("Skipping early mod loading due to previous error");
            return;
        }

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
            }
            else {
                EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
            }

            // Freeze registries
            registryHelper.freezeRegistries();

            loading = false;
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod loading", t);
            loadingException = t;
        }
    }
}
