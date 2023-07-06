package dev.su5ed.connector.mod;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DelayedRegister {
    private static final Multimap<ResourceLocation, Pair<ResourceKey<?>, Object>> registryObjects = HashMultimap.create();

    public static <V, T extends V> void register(Registry<V> registry, ResourceKey<V> key, T value) {
        registryObjects.put(registry.key().location(), Pair.of(key, value));
    }

    public static void finishRegistering() {
        List<ResourceLocation> keys = new ArrayList<>(registryObjects.keySet());
        keys.sort(ResourceLocation::compareNamespaced);

        for (ResourceLocation key : keys) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getOptional(key).orElseThrow();
            Collection<Pair<ResourceKey<?>, Object>> toRegister = registryObjects.get(key);
            if (toRegister != null) {
                for (Pair<ResourceKey<?>, Object> pair : toRegister) {
                    ((WritableRegistry) registry).register(pair.getFirst(), pair.getSecond(), Lifecycle.stable());
                }
            }
        }
    }
}
