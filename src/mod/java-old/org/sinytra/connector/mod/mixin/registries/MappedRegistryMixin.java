package org.sinytra.connector.mod.mixin.registries;

import org.sinytra.connector.mod.ConnectorMod;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
public class MappedRegistryMixin {
    @Inject(method = "freeze", at = @At("HEAD"), cancellable = true)
    private void preventFreeze(CallbackInfoReturnable<Registry<?>> cir) {
        if (ConnectorMod.preventFreeze()) {
            cir.setReturnValue((Registry<?>) this);
        }
    }
}
