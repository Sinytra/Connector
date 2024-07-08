package org.sinytra.connector.transformer.jar;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerActivity;
import net.minecraftforge.fart.api.ClassProvider;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.moddiscovery.CoreModFile;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.NotNull;
import org.sinytra.connector.locator.ConnectorEarlyLocatorBootstrap;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;
import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

public class EarlyJSCoremodTransformer implements ClassProvider {
    private static final Class<?> COREMOD_SCRIPT_LOADER = uncheck(() -> Class.forName("net.neoforged.fml.loading.CoreModScriptLoader"));
    private static final MethodHandle LOAD_COREMOD_SCRIPTS = uncheck(() -> MethodHandles.privateLookupIn(COREMOD_SCRIPT_LOADER, MethodHandles.lookup()).findStatic(COREMOD_SCRIPT_LOADER, "loadCoreModScripts", MethodType.methodType(List.class, List.class)));
    private static final MethodHandle TRANSFORM = uncheck(() -> MethodHandles.privateLookupIn(ClassTransformer.class, MethodHandles.lookup()).findVirtual(ClassTransformer.class, "transform", MethodType.methodType(byte[].class, byte[].class, String.class, String.class)));
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClassProvider provider;
    private final ClassTransformer transformer;

    public static EarlyJSCoremodTransformer create(ClassProvider classProvider, Collection<IModFile> loadedMods) {
        try {
            List<? extends ITransformer<?>> transformers = transformers(loadedMods);

            TransformStore transformStore = new TransformStore();
            ITransformationService service = new DummyService(transformers);

            Constructor<TransformationServiceDecorator> cst = TransformationServiceDecorator.class.getDeclaredConstructor(ITransformationService.class);
            cst.setAccessible(true);
            TransformationServiceDecorator decorator = cst.newInstance(service);
            decorator.gatherTransformers(transformStore);

            LaunchPluginHandler plugins = ConnectorUtil.allocateInstance(LaunchPluginHandler.class);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            pluginsField.set(plugins, new HashMap<>());

            Constructor<ClassTransformer> xformCst = ClassTransformer.class.getDeclaredConstructor(TransformStore.class, LaunchPluginHandler.class, TransformingClassLoader.class);
            xformCst.setAccessible(true);
            ClassTransformer classTransformer = xformCst.newInstance(transformStore, plugins, null);
            return new EarlyJSCoremodTransformer(classProvider, classTransformer);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public EarlyJSCoremodTransformer(ClassProvider provider, ClassTransformer transformer) {
        this.provider = provider;
        this.transformer = transformer;
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String s) {
        return this.provider.getClass(s);
    }

    @Override
    public Optional<byte[]> getClassBytes(String s) {
        return this.provider.getClassBytes(s)
            .map(bytes -> {
                try {
                    return (byte[]) TRANSFORM.invoke(this.transformer, bytes, s, ITransformerActivity.COMPUTING_FRAMES_REASON);
                } catch (Throwable t) {
                    LOGGER.error("Error transforming class {}", s, t);
                    return bytes;
                }
            });
    }

    @Override
    public void close() throws IOException {
        this.provider.close();
    }

    @SuppressWarnings("rawtypes")
    private record DummyService(List transformers) implements ITransformationService {
        @Override
        @NotNull
        public String name() {
            return "connector_early_js_coremods";
        }

        @Override
        public void initialize(IEnvironment environment) {}

        @Override
        public void onLoad(IEnvironment env, Set<String> otherServices) {}
    }

    // Stolen from FML because of access issues and LoadingModList usage

    private static List<? extends ITransformer<?>> transformers(Collection<IModFile> modFiles) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        var result = new ArrayList<>(loadCoreModScripts(modFiles));

        ILaunchContext launchContext = ConnectorEarlyLocatorBootstrap.getLaunchContext();
        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    result.add(transformer);
                }
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                    ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }

        return result;
    }

    private static List<ITransformer<?>> loadCoreModScripts(Collection<IModFile> modFiles) {
        var filesWithCoreModScripts = modFiles
            .stream()
            .filter(mf -> {
                List<CoreModFile> coremods = ((ModFile) mf).getCoreMods();
                return coremods != null && !coremods.isEmpty();
            })
            .toList();

        if (filesWithCoreModScripts.isEmpty()) {
            // Don't even bother starting the scripting engine if no mod contains scripting core mods
            LOGGER.debug(LogMarkers.CORE, "Not loading coremod script-engine since no mod requested it");
            return List.of();
        }

        LOGGER.info(LogMarkers.CORE, "Loading coremod script-engine for {}", filesWithCoreModScripts);
        try {
            return (List<ITransformer<?>>) LOAD_COREMOD_SCRIPTS.invoke(filesWithCoreModScripts);
        } catch (Throwable e) {
            var message = "Could not find the coremod script-engine, but the following mods require it: " + filesWithCoreModScripts;
            ImmediateWindowHandler.crash(message);
            throw new IllegalStateException(message, e);
        }
    }
}
