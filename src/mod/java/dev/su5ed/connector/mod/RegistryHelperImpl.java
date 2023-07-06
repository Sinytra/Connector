package dev.su5ed.connector.mod;

import dev.su5ed.sinytra.connector.loader.RegistryHelper;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class RegistryHelperImpl implements RegistryHelper {
    @Override
    public void unfreezeRegistries() {
        ((MappedRegistry<?>) BuiltInRegistries.REGISTRY).unfreeze();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ((MappedRegistry<?>) registry).unfreeze();
        }
    }

    @Override
    public void freezeRegistries() {
        BuiltInRegistries.REGISTRY.freeze();
        BuiltInRegistries.REGISTRY.forEach(Registry::freeze);
    }
}
