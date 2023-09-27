package dev.su5ed.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BuiltInRegistries.class)
public interface BuiltInRegistriesAccessor {

    @Invoker
    static void callCreateContents() {
        throw new UnsupportedOperationException();
    }
}
