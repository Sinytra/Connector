package org.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.locator.ConnectorLocator;
import org.sinytra.connector.service.hacks.ConnectorForkJoinThreadFactory;
import org.sinytra.connector.service.hacks.FabricASMFixer;
import org.sinytra.connector.service.hacks.LenientRuntimeEnumExtender;
import org.sinytra.connector.service.hacks.ModuleLayerMigrator;
import org.sinytra.connector.util.ConnectorUtil;
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

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class ConnectorLoaderService implements ITransformationService {
    private static final String NAME = "connector_loader";
    private static final String AUTHLIB_MODULE = "authlib";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final VarHandle PLUGINS = uncheck(() -> ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(LaunchPluginHandler.class, "plugins", Map.class));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
        VarHandle provider = uncheck(() -> ConnectorUtil.TRUSTED_LOOKUP.findStaticVarHandle(ImmediateWindowHandler.class, "provider", ImmediateWindowProvider.class));

        ImmediateWindowProvider original = (ImmediateWindowProvider) provider.get();
        ImmediateWindowProvider newProvider = new ImmediateWindowProvider() {
            @Override
            public void updateModuleReads(ModuleLayer layer) {
                // Setup entrypoints
                ConnectorEarlyLoader.setup();
                // Invoke mixin on a dummy class to initialize mixin plugins
                // Necessary to avoid duplicate class definition errors when a plugin loads the class that is being transformed
                uncheck(() -> Class.forName("org.sinytra.connector.mod.DummyTarget", false, Thread.currentThread().getContextClassLoader()));
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
            @Override public void crash(String message) {original.crash(message);}
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
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>) PLUGINS.get(launchPluginHandler);
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
            PLUGINS.set(launchPluginHandler, sortedPlugins);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        LoadingModList.get().getModLoadingIssues().removeIf(issue ->
            issue.translationKey().equals("fml.modloadingissue.brokenfile.fabric")
                && issue.affectedPath() != null && ConnectorLocator.shouldIgnorePath(issue.affectedPath())
        );

        if (!LoadingModList.get().hasErrors()) {
            LoadingModList.get().getModLoadingIssues().addAll(ConnectorEarlyLoader.getLoadingExceptions());
        } else {
            LOGGER.warn("Broken FML mod files found, not adding Connector locator errors");
        }
        return List.of(new Resource(IModuleLayerManager.Layer.GAME, List.of(
            FabricASMFixer.provideGeneratedClassesJar(),
            ModuleLayerMigrator.moveModule(AUTHLIB_MODULE)
        )));
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
