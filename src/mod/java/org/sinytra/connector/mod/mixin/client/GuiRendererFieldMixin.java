package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

@Mixin(value = GuiRenderer.class, priority = Integer.MIN_VALUE)
public class GuiRendererFieldMixin {
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
}
