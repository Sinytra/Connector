package dev.su5ed.sinytra.connector.mod.mixin.item;

import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PiglinAi.class)
public interface PiglinAiAccessor {

    @Invoker
    static boolean callIsBarterCurrency(ItemStack stack) {
        throw new UnsupportedOperationException();
    }
}
