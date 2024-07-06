package org.sinytra.connector.mod.mixin.registries;

import net.minecraftforge.registries.ILockableRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
public class NamespacedWrapperMixin {

    @Redirect(method = "freeze", at = @At(value = "FIELD", target = "Lnet/minecraftforge/registries/NamespacedWrapper;frozen:Z", remap = false))
    private void preventFreeze(@Coerce ILockableRegistry registry, boolean frozen) {
        // Do nothing
    }
}
