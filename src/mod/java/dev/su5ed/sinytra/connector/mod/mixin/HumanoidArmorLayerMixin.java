package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(method = "getArmorLocation", at = @At("HEAD"), cancellable = true)
    private void getForgeArmorLocation(ArmorItem p_117081_, boolean p_117082_, String p_117083_, CallbackInfoReturnable<ResourceLocation> cir) {
        if (p_117083_.contains(":")) {
            cir.setReturnValue(new ResourceLocation(p_117083_));
        }
    }
}
