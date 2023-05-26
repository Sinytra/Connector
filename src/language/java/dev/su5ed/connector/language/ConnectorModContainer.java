package dev.su5ed.connector.language;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class ConnectorModContainer extends ModContainer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Consumer<?>> INIT_FUNCTIONS = Map.of(
        "main", (Consumer<ModInitializer>) ModInitializer::onInitialize,
        "client", (Consumer<ClientModInitializer>) ClientModInitializer::onInitializeClient,
        "server", (Consumer<DedicatedServerModInitializer>) DedicatedServerModInitializer::onInitializeServer
    );

    private final Map<String, List<Class<?>>> modClasses;
    private final List<Object> modInstances = new ArrayList<>();

    public ConnectorModContainer(IModInfo info, Map<String, List<EntrypointMetadata>> entryPoints, ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
        super(info);

        LOGGER.debug(LOADING, "Creating ConnectorModContainer for {}", info.getModId());
        this.activityMap.put(ModLoadingStage.CONSTRUCT, this::constructMod);
        this.contextExtension = () -> null;

        Map<String, List<EntrypointMetadata>> activeEntryPoints = new HashMap<>();
        entryPoints.forEach((key, value) -> {
            if (key.equals("main") || key.equals("client") && FMLEnvironment.dist == Dist.CLIENT || key.equals("server") && FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                activeEntryPoints.put(key, value);
            }
        });

        if (activeEntryPoints.isEmpty()) {
            throw new RuntimeException("No available entrypoints for mod " + getModId());
        }

        Module module = gameLayer.findModule(info.getOwningFile().moduleName()).orElseThrow();
        this.modClasses = activeEntryPoints.entrySet().stream()
            .map(entry -> {
                String type = entry.getKey();
                List<Class<?>> classes = entry.getValue().stream()
                    .<Class<?>>map(metadata -> {
                        if (!metadata.getAdapter().equals("default")) {
                            throw new RuntimeException("Custom adapter entry points are not yet supported!");
                        }
                        String className = metadata.getValue();
                        try {
                            Class<?> modClass = Class.forName(module, className);
                            LOGGER.trace(LOADING, "Loaded modclass {} for entrypoint {} with {}", modClass.getName(), type, modClass.getClassLoader());
                            return modClass;
                        } catch (Throwable e) {
                            LOGGER.error(LOADING, "Failed to load class {}", className, e);
                            throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
                        }
                    })
                    .toList();
                return Pair.of(type, classes);
            })
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void constructMod() {
        for (Map.Entry<String, List<Class<?>>> entry : this.modClasses.entrySet()) {
            Consumer initFunc = INIT_FUNCTIONS.get(entry.getKey());
            for (Class<?> cls : entry.getValue()) {
                this.modInstances.add(this.constructEntryPoint((Class) cls, initFunc));
            }
        }
    }

    private <T> T constructEntryPoint(Class<T> modClass, Consumer<T> initFunc) {
        try {
            LOGGER.trace(LOADING, "Loading mod instance {} of type {}", getModId(), modClass.getName());
            T instance = modClass.getDeclaredConstructor().newInstance();
            initFunc.accept(instance);
            LOGGER.trace(LOADING, "Loaded mod instance {} of type {}", getModId(), modClass.getName());
            return instance;
        } catch (Throwable e) {
            LOGGER.error(LOADING, "Failed to create mod instance. ModID: {}, class {}", getModId(), modClass.getName(), e);
            throw new ModLoadingException(modInfo, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmod", e, modClass);
        }
    }

    @Override
    public boolean matches(Object mod) {
        return this.modInstances.contains(mod);
    }

    @Override
    public Object getMod() {
        return this.modInstances.get(0);
    }
}
