package dev.su5ed.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = RegistryDataLoader.class, priority = 2000)
public abstract class RegistryDataLoaderMixin {
    @Unique
    private static Logger CONNECTOR_LOGGER;

    @Unique
    private static Set<ResourceKey<? extends Registry<?>>> connector_injectedRegistries;

    @Shadow
    private static String registryDirPath(ResourceLocation location) {
        throw new UnsupportedOperationException();
    }

    @Inject(method = "<clinit>", at = @At(value = "FIELD", target = "Lnet/minecraft/resources/RegistryDataLoader;WORLDGEN_REGISTRIES:Ljava/util/List;", shift = At.Shift.AFTER))
    private static void captureExistingRegistries(CallbackInfo ci) {
        CONNECTOR_LOGGER = LoggerFactory.getLogger(RegistryDataLoaderMixin.class);
        CONNECTOR_LOGGER.info("Capturing existing worldgen registries");
        connector_injectedRegistries = new HashSet<>();
        for (RegistryDataLoader.RegistryData<?> registry : RegistryDataLoader.WORLDGEN_REGISTRIES) {
            connector_injectedRegistries.add(registry.key());
        }
    }

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void computeInjectedRegistries(CallbackInfo ci) {
        Set<ResourceKey<? extends Registry<?>>> injected = new HashSet<>();
        for (RegistryDataLoader.RegistryData<?> data : RegistryDataLoader.WORLDGEN_REGISTRIES) {
            if (!connector_injectedRegistries.contains(data.key())) {
                injected.add(data.key());
            }
        }
        CONNECTOR_LOGGER.info("Found {} injected RegistryDataLoader worldgen registries", injected.size());
        connector_injectedRegistries = injected;
    }

    @Redirect(method = "loadRegistryContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/RegistryDataLoader;registryDirPath(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"))
    private static String modifyRegistryDirPath(ResourceLocation location, RegistryOps.RegistryInfoLookup lookup, ResourceManager manager, ResourceKey<? extends Registry<?>> registryKey) {
        if (!location.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && connector_injectedRegistries.contains(registryKey)) {
            CONNECTOR_LOGGER.info("Using path as content location for registry {}", registryKey.location());
            return location.getPath();
        }
        return registryDirPath(location);
    }
}
