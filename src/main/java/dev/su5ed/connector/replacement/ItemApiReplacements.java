package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings("unused")
public abstract class ItemApiReplacements {

    @Redirect(
        method = "isSuitableFor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;isCorrectToolForDrops(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    public boolean hookIsSuitableFor(Item item, ItemStack stack, BlockState state) {
        return item.isCorrectToolForDrops(stack, state) || ((FabricItem) item).isSuitableFor(stack, state);
    }
}
