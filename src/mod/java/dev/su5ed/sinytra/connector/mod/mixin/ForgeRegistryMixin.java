package dev.su5ed.sinytra.connector.mod.mixin;

import com.mojang.serialization.Lifecycle;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraft.core.Holder;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgeRegistry.class)
public abstract class ForgeRegistryMixin<V> implements IForgeRegistry<V> {
    // Mixin AP complained about not finding the target method, so we use @Desc instead of a string
    @Inject(target = @Desc(value = "getDelegateOrThrow", args = Object.class, ret = Holder.Reference.class), at = @At("HEAD"), cancellable = true, remap = false)
    private void getDelegateOrThrow(V value, CallbackInfoReturnable<Holder.Reference<V>> cir) {
        if (ConnectorEarlyLoader.isLoading()) {
            cir.setReturnValue(getDelegate(value).orElseGet(() -> GameData.getWrapper(getRegistryKey(), Lifecycle.stable()).createIntrusiveHolder(value)));
        }
    }
}
