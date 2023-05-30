package dev.su5ed.connector;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class ConnectorEarlyLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Consumer<?>> INIT_FUNCTIONS = Map.of(
        "main", (Consumer<ModInitializer>) ModInitializer::onInitialize,
        "client", (Consumer<ClientModInitializer>) ClientModInitializer::onInitializeClient,
        "server", (Consumer<DedicatedServerModInitializer>) DedicatedServerModInitializer::onInitializeServer
    );
    private static final Map<String, Class<?>> INIT_TYPES = Map.of(
        "main", ModInitializer.class,
        "client", ClientModInitializer.class,
        "server", DedicatedServerModInitializer.class
    );

    private static final Map<String, List<Object>> modInstances = new HashMap<>();
    
    public static List<Object> getModInstances(String modid) {
        List<Object> instances = modInstances.get(modid);
        if (instances == null) {
            throw new RuntimeException("Mod instances not found for mod " + modid);
        }
        return instances;
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        LOGGER.debug("ConnectorEarlyLoader starting");
        // Step 1: Find all connector loader mods
        List<ModInfo> mods = LoadingModList.get().getMods().stream()
            .filter(modInfo -> modInfo.getOwningFile().requiredLanguageLoaders().stream().anyMatch(spec -> spec.languageName().equals("connector")))
            .toList();
        LOGGER.debug("Found {} mods to load", mods.size());
        // Step 2: Get GAME layer
        ModuleLayer gameLayer = Launcher.INSTANCE.findLayerManager().flatMap(manager -> manager.getLayer(IModuleLayerManager.Layer.GAME)).orElseThrow();
        for (ModInfo modInfo : mods) {
            LOGGER.debug("Loading mod {}", modInfo.getModId());
            Map<String, List<EntrypointMetadata>> entryPoints = (Map<String, List<EntrypointMetadata>>) modInfo.getModProperties().get("entrypoints");
            Map<String, List<EntrypointMetadata>> activeEntryPoints = entryPoints == null ? Map.of() : findActiveEntryPoints(entryPoints);
            List<Object> instances = constructMod(modInfo, activeEntryPoints, gameLayer);
            modInstances.put(modInfo.getModId(), instances);
        }
    }

    private static Map<String, List<EntrypointMetadata>> findActiveEntryPoints(Map<String, List<EntrypointMetadata>> entryPoints) {
        Map<String, List<EntrypointMetadata>> activeEntryPoints = new HashMap<>();
        entryPoints.forEach((key, value) -> {
            if (key.equals("main") || key.equals("client") && FMLEnvironment.dist == Dist.CLIENT || key.equals("server") && FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                activeEntryPoints.put(key, value);
            }
        });
        return activeEntryPoints;
    }

    private static List<Object> constructMod(ModInfo modInfo, Map<String, List<EntrypointMetadata>> activeEntryPoints, ModuleLayer gameLayer) {
        List<Object> instances = new ArrayList<>();

        for (Map.Entry<String, List<EntrypointMetadata>> entry : activeEntryPoints.entrySet()) {
            Consumer initFunc = INIT_FUNCTIONS.get(entry.getKey());
            Class<?> type = INIT_TYPES.get(entry.getKey());
            for (EntrypointMetadata metadata : entry.getValue()) {
                if (!metadata.getAdapter().equals("default")) {
                    throw new RuntimeException("Custom adapter entry points are not yet supported!");
                }

                try {
                    LOGGER.trace(LOADING, "Loading mod instance {} of type {}", modInfo.getModId(), type.getName());
                    Object instance = create(metadata.getValue(), type, gameLayer, modInfo);
                    instances.add(instance);
                    initFunc.accept(instance);
                    LOGGER.trace(LOADING, "Loaded mod instance {} of type {}", modInfo.getModId(), type.getName());
                } catch (Exception e) {
                    LOGGER.error(LOADING, "Failed to load class {}", type.getName(), e);
                    throw new RuntimeException(e);
                }
            }
        }

        return ImmutableList.copyOf(instances);
    }

    @SuppressWarnings("unchecked")
    private static <T> T create(String value, Class<T> type, ModuleLayer gameLayer, ModInfo modInfo) throws LanguageAdapterException {
        String[] methodSplit = value.split("::");

        if (methodSplit.length >= 3) {
            throw new LanguageAdapterException("Invalid handle format: " + value);
        }

        Class<?> c;

        Module module = gameLayer.findModule(modInfo.getOwningFile().moduleName()).orElseThrow();
        c = Class.forName(module, methodSplit[0]);

        // Hack
        ConnectorEarlyLoader.class.getModule().addReads(c.getModule());

        if (methodSplit.length == 1) {
            if (type.isAssignableFrom(c)) {
                try {
                    return (T) c.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new LanguageAdapterException(e);
                }
            } else {
                throw new LanguageAdapterException("Class " + c.getName() + " cannot be cast to " + type.getName() + "!");
            }
        } else /* length == 2 */ {
            List<Method> methodList = new ArrayList<>();

            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(methodSplit[1])) {
                    continue;
                }

                methodList.add(m);
            }

            try {
                Field field = c.getDeclaredField(methodSplit[1]);
                Class<?> fType = field.getType();

                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    throw new LanguageAdapterException("Field " + value + " must be static!");
                }

                if (!methodList.isEmpty()) {
                    throw new LanguageAdapterException("Ambiguous " + value + " - refers to both field and method!");
                }

                if (!type.isAssignableFrom(fType)) {
                    throw new LanguageAdapterException("Field " + value + " cannot be cast to " + type.getName() + "!");
                }

                return (T) field.get(null);
            } catch (NoSuchFieldException e) {
                // ignore
            } catch (IllegalAccessException e) {
                throw new LanguageAdapterException("Field " + value + " cannot be accessed!", e);
            }

            if (!type.isInterface()) {
                throw new LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.getName() + "!");
            }

            if (methodList.isEmpty()) {
                throw new LanguageAdapterException("Could not find " + value + "!");
            } else if (methodList.size() >= 2) {
                throw new LanguageAdapterException("Found multiple method entries of name " + value + "!");
            }

            final Method targetMethod = methodList.get(0);
            Object object = null;

            if ((targetMethod.getModifiers() & Modifier.STATIC) == 0) {
                try {
                    object = c.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new LanguageAdapterException(e);
                }
            }

            MethodHandle handle;

            try {
                handle = MethodHandles.lookup()
                    .unreflect(targetMethod);
            } catch (Exception ex) {
                throw new LanguageAdapterException(ex);
            }

            if (object != null) {
                handle = handle.bindTo(object);
            }

            // uses proxy as well, but this handles default and object methods
            try {
                return MethodHandleProxies.asInterfaceInstance(type, handle);
            } catch (Exception ex) {
                throw new LanguageAdapterException(ex);
            }
        }
    }
}
