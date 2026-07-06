package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter;
import org.sinytra.connector.mod.ConnectorModClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("JavaLangInvokeHandleSignature")
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
    @Shadow
    @Final
    private BlockModelLighter lighter;

    @Inject(method = "tesselateAmbientOcclusion", at = @At("HEAD"))
    private void resetLighterCache(CallbackInfo ci) {
        if (this.lighter instanceof EnhancedBlockModelLighter) {
            FullFaceCalculatorAccessor calculator = (FullFaceCalculatorAccessor) ConnectorModClient.GET_CALCULATOR.get(this.lighter);
            if (calculator.getCache() == null) {
                this.lighter.reset();
            }
        }
    }
}
