package dev.su5ed.connector.mod;

import net.fabricmc.fabric.api.item.v1.ModifyItemAttributeModifiersCallback;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ItemApiEvents {

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ModifyItemAttributeModifiersCallback.EVENT.invoker().modifyAttributeModifiers(stack, event.getSlotType(), event.getModifiers());
    }
}
