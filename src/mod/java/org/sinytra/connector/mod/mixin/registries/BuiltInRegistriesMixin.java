package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {
    @Redirect(method = "freeze", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;freeze()Lnet/minecraft/core/Registry;"))
    private static Registry<?> init(Registry<?> instance) {
        return instance;
    }
}
