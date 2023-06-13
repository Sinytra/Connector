package dev.su5ed.connector.mod.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BuiltInRegistries.class)
public interface RegistriesAccessor<T> {
	@Invoker
    static void callCreateContents() {
        throw new UnsupportedOperationException();
    }
}
