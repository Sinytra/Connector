package org.sinytra.connector.mod.mixin.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.sinytra.connector.mod.compat.ItemStackExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStack.class, priority = 500)
public abstract class ItemStackMixin implements ItemStackExtensions {

    // TODO Make sure name is the same in prod
    @Inject(method = "lambda$useOn$16", at = @At("HEAD"), cancellable = true)
    private void appyUseOn(UseOnContext pContext, UseOnContext c, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = connector_useOn(c);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Unique
    public InteractionResult connector_useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockInWorld bow = new BlockInWorld(context.getLevel(), pos, false);
        Item item = ((ItemStack) (Object) this).getItem();
        return null;
    }
}
