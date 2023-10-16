package dev.su5ed.sinytra.connector.mod.mixin.item;

import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IForgeItem.class)
public interface IForgeItemMixin {

    @Inject(method = "isPiglinCurrency", at = @At("HEAD"), cancellable = true, remap = false)
    default void redirectIsPiglinCurrency(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(PiglinAi.isBarterCurrency(stack));
    }
}
