package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.sinytra.connector.mod.compat.fieldtypes.FieldTypeUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(ItemColors.class)
public abstract class ItemColorsMixin {
    @Shadow
    @Final
    private Map<Holder.Reference<Item>, ItemColor> itemColors;

    // Added via coremod
    @Shadow(aliases = { "itemColors" })
    private IdMapper<ItemColor> connector$itemColors;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/color/item/ItemColors;itemColors:Ljava/util/Map;", shift = At.Shift.AFTER))
    private void onInit(CallbackInfo ci) {
        this.connector$itemColors = FieldTypeUtil.createRedirectingMapperSafely(
            ud -> BuiltInRegistries.ITEM.getHolder(ud).orElseThrow(),
            itemReference -> BuiltInRegistries.ITEM.getId(itemReference.value()),
            this.itemColors
        );
    }
}
