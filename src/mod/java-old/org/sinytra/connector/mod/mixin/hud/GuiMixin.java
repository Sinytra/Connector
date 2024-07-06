package org.sinytra.connector.mod.mixin.hud;

import org.sinytra.connector.mod.compat.hud.GuiExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = Gui.class, priority = 200)
public abstract class GuiMixin implements GuiExtensions {
    @Unique
    private Map<String, Boolean> connector_renderStates = new HashMap<>();

    @Override
    public boolean connector_getRenderState(String phase) {
        return this.connector_renderStates.getOrDefault(phase, false);
    }

    @Override
    public void connector_setRenderState(String phase, boolean value) {
        this.connector_renderStates.put(phase, value);
    }

    @Override
    public void connector_preRender(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
        connector_setRenderState("preRender", true);
    }

    @Override
    public void connector_postRender(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_renderHealth(GuiGraphics guiGraphics) {
        // Let mods mixin into this method
        connector_setRenderState("renderHealth", true);
    }

    @Override
    public void connector_renderArmor(GuiGraphics guiGraphics) {
        // Let mods mixin into this method
        connector_setRenderState("renderArmor", true);
    }

    @Override
    public void connector_renderHotbar(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_renderEffects(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_beforeDebugEnabled(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void onRenderHotbar(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        this.connector_renderHotbar(guiGraphics, partialTick);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void onRenderHotbar(GuiGraphics guiGraphics, CallbackInfo ci) {
        this.connector_renderEffects(guiGraphics, Minecraft.getInstance().getPartialTick());
    }
}
