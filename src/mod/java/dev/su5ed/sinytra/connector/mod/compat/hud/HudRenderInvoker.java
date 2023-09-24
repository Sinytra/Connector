package dev.su5ed.sinytra.connector.mod.compat.hud;

import dev.su5ed.sinytra.connector.mod.ConnectorMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = ConnectorMod.MODID, value = Dist.CLIENT)
public final class HudRenderInvoker {

    @SubscribeEvent
    public static void beforeRenderHud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().gui instanceof GuiExtensions ext) {
            ext.connector_preRender(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void afterRenderHud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().gui instanceof GuiExtensions ext) {
            ext.connector_postRender(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    private HudRenderInvoker() {}
}
