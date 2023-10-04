package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PotionBrewing.Mix.class, priority = 9999)
public class PotionBrewingMixMixin<T> {
    @Unique
    public T connector$from;
    @Unique
    public T connector$to;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(IForgeRegistry<T> registry, T from, Ingredient ingredient, T to, CallbackInfo ci) {
        this.connector$from = from;
        this.connector$to = to;
    }
}
