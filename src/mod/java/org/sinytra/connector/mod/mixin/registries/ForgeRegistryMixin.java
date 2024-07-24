package org.sinytra.connector.mod.mixin.registries;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IForgeRegistry;
import org.sinytra.connector.mod.ConnectorLoader;
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
        if (!ConnectorLoader.hasFinishedLoading()) {
            cir.setReturnValue(getDelegate(value).orElseGet(() -> {
                MappedRegistry<V> registry = GameData.getWrapper(getRegistryKey(), Lifecycle.stable());
                try {
                    // Create intrusive holder on the registry to bind the key later
                    return registry.createIntrusiveHolder(value);
                } catch (IllegalStateException e) {
                    // Fallback if the registry does not support intrusive holders
                    return Holder.Reference.createIntrusive(registry.holderOwner(), value);
                }
            }));
        }
    }
}
