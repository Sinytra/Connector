package dev.su5ed.sinytra.connector.mod.compat.hud;

import net.minecraft.client.gui.GuiGraphics;

public interface GuiExtensions {
    default boolean connector_wrapCancellableCall(String phase, Runnable runnable) {
        connector_setRenderState(phase, false);
        runnable.run();
        if (!connector_getRenderState(phase)) {
            return false;
        }
        return true;
    }

    boolean connector_getRenderState(String phase);
    void connector_setRenderState(String phase, boolean value);

    void connector_preRender(GuiGraphics guiGraphics, float tickDelta);

    void connector_postRender(GuiGraphics guiGraphics, float tickDelta);

    void connector_renderHealth(GuiGraphics guiGraphics);

    void connector_renderArmor(GuiGraphics guiGraphics);

    void connector_renderFood(GuiGraphics guiGraphics);

    void connector_renderHotbar(GuiGraphics guiGraphics, float tickDelta);

    void connector_renderEffects(GuiGraphics guiGraphics, float tickDelta);

    void connector_beforeDebugEnabled(GuiGraphics guiGraphics, float tickDelta);
}
