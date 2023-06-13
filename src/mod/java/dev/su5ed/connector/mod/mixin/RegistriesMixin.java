package dev.su5ed.connector.mod.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BuiltInRegistries.class)
public class RegistriesMixin {
    @Redirect(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;createContents()V"))
    private static void init() {
        // NO-OP, we call this ourselves elsewhere
    }
}
