package dev.su5ed.sinytra.connector.loader;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.discovery.BuiltinMetadataWrapper;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

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

    public static void setup() {
        LOGGER.debug("ConnectorEarlyLoader starting");
        try {
            // Step 1: Find all connector loader mods
            List<ModInfo> mods = LoadingModList.get().getMods().stream()
                .filter(modInfo -> modInfo.getOwningFile().requiredLanguageLoaders().stream().anyMatch(spec -> spec.languageName().equals(ConnectorUtil.CONNECTOR_LANGUAGE)))
                .toList();
            // Step 2: Propagate mods to fabric
            FabricLoaderImpl.INSTANCE.addFmlMods(mods);
            FabricLoaderImpl.INSTANCE.addMods(List.of(createMinecraftMod()));
            FabricLoaderImpl.INSTANCE.setup();
            // Step 3: Call prelaunch entrypoints
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
            // Step 3: Invoke entry points
            EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                EntrypointUtils.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
            }
            else {
                EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
            }
            loading = false;
        } catch (Throwable t) {
            LOGGER.error("Encountered error during early mod loading", t);
            loadingException = t;
        }
    }

    private static ModContainerImpl createMinecraftMod() {
        IModInfo modInfo = LoadingModList.get().getModFileById("minecraft").getMods().get(0);
        ModMetadata metadata = new BuiltinModMetadata.Builder("minecraft", FMLLoader.versionInfo().mcVersion())
            .setName("Minecrtaft")
            .build();
        LoaderModMetadata loaderMetadata = new BuiltinMetadataWrapper(metadata);
        return new ModContainerImpl(modInfo, loaderMetadata);
    }
}
