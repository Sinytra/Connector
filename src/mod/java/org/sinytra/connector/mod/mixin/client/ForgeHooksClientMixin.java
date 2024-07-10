package org.sinytra.connector.mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientHooks.class)
public abstract class ForgeHooksClientMixin {
    @ModifyReturnValue(method = "gatherTooltipComponents(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/util/Optional;IIILnet/minecraft/client/gui/Font;)Ljava/util/List;", at = @At("RETURN"))
    private static List<ClientTooltipComponent> makeTooltipComponentListMutable(List<ClientTooltipComponent> list) {
        return new ArrayList<>(list);
    }
}
