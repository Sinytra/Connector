package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.resources.RegistryDataLoader;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(DataPackRegistriesHooks.class)
public interface DataPackRegistriesHooksAccessor {
    @Accessor("DATA_PACK_REGISTRIES")
    @Mutable
    static void set_DATA_PACK_REGISTRIES(List<RegistryDataLoader.RegistryData<?>> list) {
        throw new UnsupportedOperationException();
    }

    @Accessor("DATA_PACK_REGISTRIES_VIEW")
    @Mutable
    static void set_DATA_PACK_REGISTRIES_VIEW(List<RegistryDataLoader.RegistryData<?>> list) {
        throw new UnsupportedOperationException();
    }
}
