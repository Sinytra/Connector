package org.sinytra.connector.mod.mixin.item;

import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IItemExtension.class)
public interface IItemExtensionMixin {
    @Inject(method = "isPiglinCurrency", at = @At("HEAD"), cancellable = true)
    default void redirectIsPiglinCurrency(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(PiglinAi.isBarterCurrency(stack));
    }
}
