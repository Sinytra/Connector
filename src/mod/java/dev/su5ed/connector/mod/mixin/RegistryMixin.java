package dev.su5ed.connector.mod.mixin;

import dev.su5ed.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.connector.mod.DelayedRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Registry.class)
public interface RegistryMixin {

    @Inject(method = "register(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private static <V, T extends V> void delayRegister(Registry<V> registry, ResourceKey<V> key, T value, CallbackInfoReturnable<V> cir) {
        if (ConnectorEarlyLoader.isLoading()) {
            DelayedRegistrar.register(registry, key, value);
            cir.setReturnValue(value);
        }
    }
}
