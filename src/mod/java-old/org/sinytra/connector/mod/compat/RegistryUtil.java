package org.sinytra.connector.mod.compat;

import com.google.common.collect.BiMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.sinytra.connector.loader.ConnectorEarlyLoader;
import net.fabricmc.fabric.impl.registry.sync.DynamicRegistriesImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class RegistryUtil {
    private static final VarHandle REGISTRY_NAMES = uncheck(() -> MethodHandles.privateLookupIn(ForgeRegistry.class, MethodHandles.lookup()).findVarHandle(ForgeRegistry.class, "names", BiMap.class));
    private static final ResourceLocation PARTICLE_TYPE_REGISTRY = ForgeRegistries.Keys.PARTICLE_TYPES.location();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isRegisteredFabricDynamicRegistry(ResourceKey<?> key) {
        return DynamicRegistriesImpl.FABRIC_DYNAMIC_REGISTRY_KEYS.stream().anyMatch(key::equals);
    }

    private static <V> BiMap<ResourceLocation, V> getNames(ForgeRegistry<V> registry) {
        return (BiMap<ResourceLocation, V>) uncheck(() -> REGISTRY_NAMES.get(registry));
    }
}
