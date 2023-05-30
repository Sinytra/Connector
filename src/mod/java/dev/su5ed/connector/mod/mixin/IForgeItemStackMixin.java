package dev.su5ed.connector.mod.mixin;

import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Implements Fabric crafting item remainders
@Mixin(IForgeItemStack.class)
public interface IForgeItemStackMixin {

    // TODO IForgeItem mixin
    @Overwrite
    default boolean hasCraftingRemainingItem() {
        return ((ItemStack) (Object) this).getItem().hasCraftingRemainingItem((ItemStack) (Object) this) || this instanceof FabricItemStack fabricItemStack && !fabricItemStack.getRecipeRemainder().isEmpty();
    }
    
    @Overwrite
    default ItemStack getCraftingRemainingItem() {
        return this instanceof FabricItemStack fabricItemStack ? fabricItemStack.getRecipeRemainder() : ((ItemStack) (Object) this).getItem().getCraftingRemainingItem((ItemStack) (Object) this);
    }
}
