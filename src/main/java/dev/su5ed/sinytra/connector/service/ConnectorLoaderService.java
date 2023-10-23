package dev.su5ed.sinytra.connector.service;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.unsafe.UnsafeHacks;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConnectorLoaderService implements ITransformationService {
    private static final String NAME = "connector_loader";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
        Runnable original = FMLLoader.progressWindowTick;
        FMLLoader.progressWindowTick = () -> {
            ConnectorEarlyLoader.setup();
            FabricMixinBootstrap.init();
            FabricASMFixer.injectMinecraftModuleReader();

            FMLLoader.progressWindowTick = original;
            original.run();
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
            // Sort launch plugins
            LinkedHashMap<String, ILaunchPluginService> sortedPlugins = new LinkedHashMap<>();
            // Runtime Enum extender will fail if a mixin makes $VALUES mutable first, so it must come before us as well
            sortedPlugins.put("runtime_enum_extender", plugins.remove("runtime_enum_extender"));
            // Mixin must come after
            sortedPlugins.put("mixin", plugins.remove("mixin"));
            // The rest goes to the end
            sortedPlugins.putAll(plugins);
            UnsafeHacks.setField(pluginsField, launchPluginHandler, sortedPlugins);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        LoadingModList.get().getErrors().addAll(ConnectorEarlyLoader.getLoadingExceptions());
        return List.of(new Resource(IModuleLayerManager.Layer.GAME, List.of(new FabricASMFixer.FabricASMGeneratedClassesSecureJar())));
    }

    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
