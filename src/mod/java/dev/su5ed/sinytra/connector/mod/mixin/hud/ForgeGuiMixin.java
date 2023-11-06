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

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false)
    private void onRenderFood(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        ((GuiExtensions) this).connector_renderFood(guiGraphics);
    }

    @Inject(method = "renderHUDText", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDebug:Z", opcode = Opcodes.GETFIELD))
    private void onRenderDebug(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        ((GuiExtensions) this).connector_beforeDebugEnabled(guiGraphics, Minecraft.getInstance().getPartialTick());
    } 
}
