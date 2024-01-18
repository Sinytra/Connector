package dev.su5ed.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.service.hacks.ConnectorForkJoinThreadFactory;
import dev.su5ed.sinytra.connector.service.hacks.ModuleLayerMigrator;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.unsafe.UnsafeHacks;
import org.slf4j.Logger;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;
import static dev.su5ed.sinytra.connector.service.hacks.ModuleLayerMigrator.TRUSTED_LOOKUP;

public class ConnectorLoaderService implements ITransformationService {
    private static final String NAME = "connector_loader";
    private static final String AUTHLIB_MODULE = "authlib";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
        VarHandle provider = uncheck(() -> TRUSTED_LOOKUP.findStaticVarHandle(ImmediateWindowHandler.class, "provider", ImmediateWindowProvider.class));

        ImmediateWindowProvider original = (ImmediateWindowProvider) provider.get();
        ImmediateWindowProvider newProvider = new ImmediateWindowProvider() {

            @Override
            public void updateModuleReads(ModuleLayer layer) {
                // Setup entrypoints
                ConnectorEarlyLoader.setup();
                // Invoke mixin on a dummy class to initialize mixin plugins
                // Necessary to avoid duplicate class definition errors when a plugin loads the class that is being transformed
                uncheck(() -> Class.forName("dev.su5ed.sinytra.connector.mod.DummyTarget", false, Thread.currentThread().getContextClassLoader()));
                // Run preLaunch
                ConnectorEarlyLoader.preLaunch();
                original.updateModuleReads(layer);
            }

            //@formatter:off
            @Override public String name() {return original.name();}
            @Override public Runnable initialize(String[] arguments) {return original.initialize(arguments);}
            @Override public void updateFramebufferSize(IntConsumer width, IntConsumer height) {original.updateFramebufferSize(width, height);}
            @Override public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {return original.setupMinecraftWindow(width, height, title, monitor);}
            @Override public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {return original.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);}
            @Override public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {return original.loadingOverlay(mc, ri, ex, fade);}
            @Override public void periodicTick() {original.periodicTick();}
            @Override public String getGLVersion() {return original.getGLVersion();}
            //@formatter:on
        };
        provider.set(newProvider);
        ConnectorForkJoinThreadFactory.install();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        List<ILaunchPluginService> injectPlugins = List.of(new ConnectorPreLaunchPlugin());

        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
            // Sort launch plugins
            LinkedHashMap<String, ILaunchPluginService> sortedPlugins = new LinkedHashMap<>();
            // Mixin must come first
            sortedPlugins.put("mixin", plugins.remove("mixin"));
            // Handle cases where a mixin has already made the enum mutable
            plugins.remove("runtime_enum_extender");
            sortedPlugins.put("runtime_enum_extender", new LenientRuntimeEnumExtender());
            // Our plugins come after mixin
            injectPlugins.forEach(plugin -> sortedPlugins.put(plugin.name(), plugin));
            // The rest goes to the end
            sortedPlugins.putAll(plugins);
            UnsafeHacks.setField(pluginsField, launchPluginHandler, sortedPlugins);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        if (LoadingModList.get().getBrokenFiles().isEmpty()) {
            LoadingModList.get().getErrors().addAll(ConnectorEarlyLoader.getLoadingExceptions());
        } else {
            LOGGER.warn("Broken FML mod files found, not adding Connector locator errors");
        }
        return List.of(new Resource(IModuleLayerManager.Layer.GAME, List.of(
            new FabricASMFixer.FabricASMGeneratedClassesSecureJar(),
            ModuleLayerMigrator.moveModule(AUTHLIB_MODULE)
        )));
    }

    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
