package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/gui/PictureInPictureRendererPool;createPools(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Ljava/util/List;)Ljava/util/Map;"))
    private List<?> wrapPictureInPictureRenderers(List<?> factories) {
        return factories.stream()
            .map(factory -> {
                if (factory instanceof PictureInPictureRenderer<?> renderer) {
                    return new PictureInPictureRendererRegistration(renderer.getRenderStateClass(), buf -> renderer);
                }
                return factory;
            })
            .toList();
    }
}
