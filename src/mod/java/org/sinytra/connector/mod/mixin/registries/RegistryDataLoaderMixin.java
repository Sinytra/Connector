package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.resources.RegistryDataLoader;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import org.sinytra.connector.mod.ConnectorMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(value = RegistryDataLoader.class, priority = 9999)
public abstract class RegistryDataLoaderMixin {

    // Update trackers after the value of WORLDGEN_REGISTRIES has changed 
    // https://github.com/quiqueck/WorldWeaver/blob/8861dbf39c85cdafbaf2caab1783d11c26d78f44/wover-core-api/src/main/java/org/betterx/wover/core/mixin/registry/RegistryDataLoaderMixin.java#L28
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void postInitLast(CallbackInfo ci) {
        if (RegistryDataLoader.WORLDGEN_REGISTRIES.size() != DataPackRegistriesHooks.getDataPackRegistries().size()) {
            ConnectorMod.LOGGER.info("Detected changes in WORLDGEN_REGISTRIES, updating NeoForge references");
            List<RegistryDataLoader.RegistryData<?>> list = new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES); 
            DataPackRegistriesHooksAccessor.set_DATA_PACK_REGISTRIES(list);
            DataPackRegistriesHooksAccessor.set_DATA_PACK_REGISTRIES_VIEW(Collections.unmodifiableList(list));
        }
    }
}
