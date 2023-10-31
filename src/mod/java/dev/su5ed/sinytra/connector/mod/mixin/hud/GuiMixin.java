package dev.su5ed.sinytra.connector.mod.mixin.hud;

import dev.su5ed.sinytra.connector.mod.compat.hud.GuiExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 200)
public abstract class GuiMixin implements GuiExtensions {

    @Override
    public void connector_preRender(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_postRender(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_renderFood(GuiGraphics guiGraphics) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_renderHotbar(GuiGraphics guiGraphics, float tickDelta) {
        // Let mods mixin into this method
    }

    @Override
    public void connector_renderEffects(GuiGraphics guiGraphics, float tickDelta) {
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
