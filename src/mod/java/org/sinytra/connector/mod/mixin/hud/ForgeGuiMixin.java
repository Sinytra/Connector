package org.sinytra.connector.mod.mixin.hud;

import org.sinytra.connector.mod.compat.hud.GuiExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeGui.class)
public class ForgeGuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        ((GuiExtensions) this).connector_setRenderState("enableStatusBarRender", true);
    }

    @Inject(method = "renderHealth", at = @At("HEAD"), remap = false, cancellable = true)
    private void onRenderHealth(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        GuiExtensions ext = (GuiExtensions) this;
        if (!ext.connector_getRenderState("enableStatusBarRender") || !ext.connector_wrapCancellableCall("renderHealth", () -> ext.connector_renderHealth(guiGraphics))) {
            ci.cancel();
            ext.connector_setRenderState("enableStatusBarRender", false);
        }
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), remap = false, cancellable = true)
    private void onRenderArmor(GuiGraphics guiGraphics, int width, int height, CallbackInfo ci) {
        GuiExtensions ext = (GuiExtensions) this;
        if (!ext.connector_getRenderState("enableStatusBarRender") || !ext.connector_wrapCancellableCall("renderArmor", () -> ext.connector_renderArmor(guiGraphics))) {
            ci.cancel();
            ext.connector_setRenderState("enableStatusBarRender", false);
        }
    }

    @Inject(method = "renderHUDText", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDebug:Z", opcode = Opcodes.GETFIELD))
    private void onRenderDebug(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        ((GuiExtensions) this).connector_beforeDebugEnabled(guiGraphics, Minecraft.getInstance().getPartialTick());
    }
}
