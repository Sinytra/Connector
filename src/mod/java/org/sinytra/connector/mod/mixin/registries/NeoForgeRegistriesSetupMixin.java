package org.sinytra.connector.mod.mixin.registries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NeoForgeRegistriesSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(NeoForgeRegistriesSetup.class)
public class NeoForgeRegistriesSetupMixin {

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;addListener(Ljava/util/function/Consumer;)V", ordinal = 1))
    private static void setup(IEventBus bus, Consumer<?> consumer) {
        // Skip
    }
}
