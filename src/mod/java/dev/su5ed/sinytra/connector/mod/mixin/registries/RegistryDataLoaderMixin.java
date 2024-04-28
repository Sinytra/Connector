package dev.su5ed.sinytra.connector.mod.mixin.registries;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.mod.compat.RegistryUtil;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DataPackRegistriesHooks;
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
        if (!location.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && (connector_injectedRegistries.contains(registryKey) || connector$shouldOmitPrefix(location, registryKey, manager))) {
            CONNECTOR_LOGGER.info("Using path as content location for registry {}", registryKey.location());
            return location.getPath();
        }
        return registryDirPath(location);
    }

    @Unique
    private static boolean connector$shouldOmitPrefix(ResourceLocation location, ResourceKey<? extends Registry<?>> registryKey, ResourceManager manager) {
        String modid = location.getNamespace();
        // Fabric mod registries added directly to RegistryDataLoader.WORLDGEN_REGISTRIES should not be prefixed
        if (ConnectorEarlyLoader.isConnectorMod(modid) && ModList.get().isLoaded("fabric_registry_sync_v0") && !RegistryUtil.isRegisteredFabricDynamicRegistry(registryKey)) {
            return true;
        }
        // Check if the registry has been registered
        if (DataPackRegistriesHooks.getDataPackRegistries().stream().noneMatch(data -> registryKey.equals(data.key()))) {
            // If the namespace is one of a fabric mod, omit the prefix
            if (ConnectorEarlyLoader.isConnectorMod(modid)) {
                return true;
            }
            // If the namespace is of a known forge mod, it must stay prefixed
            if (ModList.get().isLoaded(modid)) {
                return false;
            }
            // In case the namespace does not belong to any mod, make an educated guess
            // Omit the prefix in cases where no resources exist at the prefixed path, but exist at the standard one
            if (FileToIdConverter.json(ForgeHooks.prefixNamespace(location)).listMatchingResources(manager).isEmpty()) {
                return !FileToIdConverter.json(location.getPath()).listMatchingResources(manager).isEmpty();
            }
        }
        return false;
    }
}
