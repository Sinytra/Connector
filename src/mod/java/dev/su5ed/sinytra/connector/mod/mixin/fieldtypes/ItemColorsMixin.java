package dev.su5ed.sinytra.connector.mod.mixin.fieldtypes;

import dev.su5ed.sinytra.connector.mod.compat.fieldtypes.FieldTypeUtil;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
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
    private IdMapper<ItemColor> connector$itemColors;

    @Unique
    public IdMapper<ItemColor> connector$getItemColors() {
        if (this.connector$itemColors == null) {
            this.connector$itemColors = FieldTypeUtil.createRedirectingMapperSafely(
                i -> ForgeRegistries.ITEMS.getDelegateOrThrow(BuiltInRegistries.ITEM.byId(i)),
                itemReference -> BuiltInRegistries.ITEM.getId(itemReference.get()),
                this.itemColors
            );
        }
        return this.connector$itemColors;
    }
}
