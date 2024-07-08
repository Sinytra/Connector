package org.sinytra.connector.mod.mixin.registries;

import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistriesSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NeoForgeRegistriesSetup.class)
public interface NeoForgeRegistriesSetupAccessor {
    @Invoker
    static void invokeModifyRegistries(ModifyRegistriesEvent event) {
        // NOOP
    }
}
