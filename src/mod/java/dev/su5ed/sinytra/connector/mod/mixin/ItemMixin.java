package dev.su5ed.sinytra.connector.mod.mixin;

import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Applied only if FAPI is present.<br>
 * Contains adapter methods for redirecting fabric extension interface calls to forge.
 * @see dev.su5ed.sinytra.connector.transformer.ForgeApiRedirects
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Unique
    public Multimap<Attribute, AttributeModifier> connector_getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        return ((Item) (Object) this).getAttributeModifiers(slot, stack);
    }

    @Unique
    public boolean connector_allowContinuingBlockBreaking(Player player, ItemStack oldStack, ItemStack newStack) {
        Item item = (Item) (Object) this;
        return ((FabricItem) item).allowContinuingBlockBreaking(player, oldStack, newStack) || !item.shouldCauseBlockBreakReset(oldStack, newStack);
    }

    @Unique
    public boolean connector_allowNbtUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return ((Item) (Object) this).shouldCauseReequipAnimation(oldStack, newStack, false);
    }
}
