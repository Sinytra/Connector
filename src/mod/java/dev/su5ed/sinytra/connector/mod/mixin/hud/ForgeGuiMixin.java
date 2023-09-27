package dev.su5ed.sinytra.connector.mod.mixin.hud;

import dev.su5ed.sinytra.connector.mod.compat.hud.GuiExtensions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeGui.class)
public class ForgeGuiMixin {

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false)
    public void onRenderFood(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        ((GuiExtensions) this).connector_renderFood(guiGraphics);
    }
}
