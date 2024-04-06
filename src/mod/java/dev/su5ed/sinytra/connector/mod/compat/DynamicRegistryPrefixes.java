package dev.su5ed.sinytra.connector.mod.compat;

import net.fabricmc.fabric.impl.registry.sync.DynamicRegistriesImpl;
import net.minecraft.resources.ResourceKey;

public class DynamicRegistryPrefixes {
    public static boolean isRegisteredFabricDynamicRegistry(ResourceKey<?> key) {
        return DynamicRegistriesImpl.FABRIC_DYNAMIC_REGISTRY_KEYS.stream().anyMatch(key::equals);
    }
}
