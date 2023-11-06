package dev.su5ed.sinytra.connector.mod.compat.hud;

import net.minecraft.client.gui.GuiGraphics;

public interface GuiExtensions {
    boolean isConnector_didFinishPreRender();

    void resetConnector_didFinishPreRender();

    void connector_preRender(GuiGraphics guiGraphics, float tickDelta);

    void connector_postRender(GuiGraphics guiGraphics, float tickDelta);

    void connector_renderFood(GuiGraphics guiGraphics);

    void connector_renderHotbar(GuiGraphics guiGraphics, float tickDelta);

    void connector_renderEffects(GuiGraphics guiGraphics, float tickDelta);

    void connector_beforeDebugEnabled(GuiGraphics guiGraphics, float tickDelta);
}
