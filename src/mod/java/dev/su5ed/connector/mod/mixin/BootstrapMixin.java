package dev.su5ed.connector.mod.mixin;

import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Bootstrap.class)
public abstract class BootstrapMixin {

    @Redirect(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/GameData;vanillaSnapshot()V"))
    private static void skipRegistryLock() {
        // NO OP
    }
}
