package dev.su5ed.sinytra.connector.mod.mixin.fieldtypes;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(ItemColors.class)
public class ItemColorsMixin {
    @Shadow
    @Final
    private Map<Holder.Reference<Item>, ItemColor> itemColors;
    @Unique
    public IdMapper<ItemColor> connector$itemColors = new IdMapper<>();

    @Unique
    public IdMapper<ItemColor> connector$getItemColors() {
        if (this.itemColors.size() != this.connector$itemColors.size()) {
            if (this.itemColors.size() < this.connector$itemColors.size()) throw new IllegalStateException("Tried to register new item colors using connector$getItemColors()!");
            this.itemColors.forEach((item, color) -> {
                this.connector$itemColors.addMapping(color, BuiltInRegistries.ITEM.getId(item.get()));
            });
        }
        return this.connector$itemColors;
    }
}
