package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
public abstract class RenderingApiReplacements {

    @Redirect(
        method = {"renderTooltip(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;Ljava/util/Optional;II)V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;gatherTooltipComponents(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/util/Optional;IIILnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/Font;)Ljava/util/List;")
    )
    private List<ClientTooltipComponent> injectRenderTooltipLambda(ItemStack stack, List<? extends FormattedText> textElements, Optional<TooltipComponent> itemComponent, int mouseX, int screenWidth, int screenHeight, @Nullable Font forcedFont, Font fallbackFont) {
        List<ClientTooltipComponent> list = new ArrayList<>(ForgeHooksClient.gatherTooltipComponents(stack, textElements, itemComponent, mouseX, screenWidth, screenHeight, forcedFont, fallbackFont));

        // Trying to avoid a lambda
        if (itemComponent.isPresent()) {
            ClientTooltipComponent component = TooltipComponentCallback.EVENT.invoker().getComponent(itemComponent.get());
            if (component != null) {
                list.add(1, component);
            }
        }

        return Collections.unmodifiableList(list);
    }
}
