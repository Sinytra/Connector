package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(method = "getArmorLocation", at = @At("HEAD"), cancellable = true)
    private void getForgeArmorLocation(ArmorItem armorItem, boolean layer2, @Nullable String suffix, CallbackInfoReturnable<ResourceLocation> cir) {
        if (suffix != null && suffix.contains(":")) {
            cir.setReturnValue(new ResourceLocation(suffix));
        }
    }
}
