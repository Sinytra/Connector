package dev.su5ed.sinytra.connector.mod.mixin.hud;

import dev.su5ed.sinytra.connector.mod.compat.hud.GuiExtensions;
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

    @Inject(method = "renderHealth", at = @At("HEAD"), remap = false, cancellable = true)
    private void onRenderHealth(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        GuiExtensions ext = (GuiExtensions) this;
        ext.resetConnector_didFinishStatusBarRender();
        ext.connector_renderHealth(guiGraphics);
        if (!ext.isConnector_didFinishStatusBarRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), remap = false, cancellable = true)
    private void onRenderArmor(GuiGraphics guiGraphics, int width, int height, CallbackInfo ci) {
        GuiExtensions ext = (GuiExtensions) this;
        if (!ext.isConnector_didFinishStatusBarRender()) {
            ci.cancel();
        } else {
            ext.connector_renderArmor(guiGraphics);
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false, cancellable = true)
    private void onRenderFood(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        GuiExtensions ext = (GuiExtensions) this;
        if (!ext.isConnector_didFinishStatusBarRender()) {
            ci.cancel();
        } else {
            ext.connector_renderFood(guiGraphics);
        }
    }

    @Inject(method = "renderHUDText", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDebug:Z", opcode = Opcodes.GETFIELD))
    private void onRenderDebug(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        ((GuiExtensions) this).connector_beforeDebugEnabled(guiGraphics, Minecraft.getInstance().getPartialTick());
    }
}
