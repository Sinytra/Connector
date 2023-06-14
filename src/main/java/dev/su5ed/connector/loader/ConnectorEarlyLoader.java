package dev.su5ed.connector.loader;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean loading;

    public static Collection<Object> getModInstances(String modid) {
        Collection<Object> instances = FabricLoaderImpl.INSTANCE.getModInstances(modid);
        if (instances == null) {
            throw new RuntimeException("Mod instances not found for mod " + modid);
        }
        return instances;
    }

    public static boolean isLoading() {
        return loading;
    }

    public static void setup() {
        // TODO HANDLE AND PROPAGATE EXCEPTIONS
        LOGGER.debug("ConnectorEarlyLoader starting");
        // Step 1: Find all connector loader mods
        List<ModInfo> mods = LoadingModList.get().getMods().stream()
            .filter(modInfo -> modInfo.getOwningFile().requiredLanguageLoaders().stream().anyMatch(spec -> spec.languageName().equals("connector")))
            .toList();
        // Step 2: Propagate mods to fabric
        FabricLoaderImpl.INSTANCE.setup(mods);
        // Step 3: Call prelaunch entrypoints
        EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
    }

    public static void load() {
        loading = true;
        // Step 3: Invoke entry points
        EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EntrypointUtils.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
        } else {
            EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
        }
        loading = false;
    }
}
