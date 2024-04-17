package dev.su5ed.sinytra.connector.mod.mixin.registries;

import dev.su5ed.sinytra.connector.mod.compat.RegistryUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeRegistry.class)
public abstract class ClientForgeRegistryMixin<V> implements IForgeRegistry<V> {

    @Inject(method = "sync", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;clear()V", ordinal = 0), remap = false)
    private void retainFabricClientEntries(ResourceLocation name, ForgeRegistry<V> from, CallbackInfo ci) {
        RegistryUtil.retainFabricClientEntries(name, from, this);
    }
}
