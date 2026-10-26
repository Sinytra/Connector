package org.sinytra.connector.mod.mixin.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Shadow
    @Final
    private GuiRenderState renderState;
    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    // Added by GuiRendererFieldMixin
    @Shadow
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/gui/PictureInPictureRendererPool;createPools(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Ljava/util/List;)Ljava/util/Map;"))
    private List<?> collectPictureInPictureRenderers(List<?> factories) {
        ImmutableMap.Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> renderers = ImmutableMap.builder();
        List<Object> registrations = new ArrayList<>();
        for (Object factory : factories) {
            if (factory instanceof PictureInPictureRenderer<?> renderer) {
                renderers.put(renderer.getRenderStateClass(), renderer);
            } else {
                registrations.add(factory);
            }
        }
        this.pictureInPictureRenderers = renderers.buildOrThrow();
        return registrations;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "preparePictureInPictureState", at = @At("HEAD"), cancellable = true)
    private <T extends PictureInPictureRenderState> void prepareVanillaRenderer(T state, int guiScale, boolean firstPass, CallbackInfoReturnable<Boolean> cir) {
        PictureInPictureRenderer<T> renderer = this.pictureInPictureRenderers != null ? (PictureInPictureRenderer<T>) this.pictureInPictureRenderers.get(state.getClass()) : null;
        if (renderer != null) {
            if (firstPass) {
                renderer.prepare(state, this.renderState, this.featureRenderDispatcher, guiScale);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void closeVanillaRenderers(CallbackInfo ci) {
        if (this.pictureInPictureRenderers != null) {
            this.pictureInPictureRenderers.values().forEach(PictureInPictureRenderer::close);
        }
    }
}
