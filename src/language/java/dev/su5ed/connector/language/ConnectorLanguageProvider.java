package dev.su5ed.connector.language;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;
import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class ConnectorLanguageProvider implements IModLanguageProvider {
    // TODO Read from locator
    public static final String CONNECTOR_LANGUAGE = "connector";

    private record ConnectorModTarget(String modId, Map<String, List<EntrypointMetadata>> entryPoints) implements IModLanguageProvider.IModLanguageLoader {
        private static final Logger LOGGER = LogUtils.getLogger();

        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
            // This language class is loaded in the system level classloader - before the game even starts
            // So we must treat container construction as an arms length operation, and load the container
            // in the classloader of the game - the context classloader is appropriate here.
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                Class<?> modContainer = Class.forName("dev.su5ed.connector.language.ConnectorModContainer", true, classLoader);
                LOGGER.debug(LOADING, "Loading ConnectorModContainer from classloader {} - got {}", classLoader, modContainer.getClassLoader());
                Constructor<?> constructor = modContainer.getConstructor(IModInfo.class, Map.class, ModFileScanData.class, ModuleLayer.class);
                return (T) constructor.newInstance(info, this.entryPoints, modFileScanResults, gameLayer);
            } catch (InvocationTargetException e) {
                LOGGER.error(LOADING, "Failed to build mod", e);
                if (e.getTargetException() instanceof ModLoadingException mle) {
                    throw mle;
                } else {
                    throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
                }
            } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                LOGGER.error(LOADING, "Unable to load ConnectorModContainer", e);
                Class<RuntimeException> mle = (Class<RuntimeException>) uncheck(() -> Class.forName("net.minecraftforge.fml.ModLoadingException", true, classLoader));
                Class<ModLoadingStage> mls = (Class<ModLoadingStage>) uncheck(() -> Class.forName("net.minecraftforge.fml.ModLoadingStage", true, classLoader));
                throw uncheck(() -> uncheck(() -> mle.getConstructor(IModInfo.class, mls, String.class, Throwable.class)).newInstance(info, Enum.valueOf(mls, "CONSTRUCT"), "fml.modloading.failedtoloadmodclass", e));
            }
        }
    }

    @Override
    public String name() {
        return CONNECTOR_LANGUAGE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return scanResult -> {
            Map<String, ConnectorModTarget> modTargetMap = scanResult.getIModInfoData().stream()
                .flatMap(fi -> fi.getMods().stream())
                .map(modInfo -> {
                    Map<String, List<EntrypointMetadata>> entryPoints = (Map<String, List<EntrypointMetadata>>) modInfo.getModProperties().get("entrypoints");
                    return new ConnectorModTarget(modInfo.getModId(), entryPoints);
                })
                .collect(Collectors.toMap(ConnectorModTarget::modId, Function.identity(), (a, b) -> a));
            scanResult.addLanguageLoader(modTargetMap);
        };
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(Supplier<R> consumeEvent) {}
}
