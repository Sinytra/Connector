package dev.su5ed.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltInRegistries.class)
public class BuiltInRegistriesMixin {
    @Unique
    private static boolean hasInitialised = false;

    @Inject(method = "createContents", at = @At("HEAD"), cancellable = true)
    private static void init(CallbackInfo ci) {
        if (hasInitialised) {
            ci.cancel();
        }

        hasInitialised = true;
    }
}
