package dev.su5ed.sinytra.connector.mod.mixin.item;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ItemStack.class, priority = 9999)
public class LateItemStackMixin {
    // Dummy class to serve as an entrypoint for our mixin plugin ASM hook
}
