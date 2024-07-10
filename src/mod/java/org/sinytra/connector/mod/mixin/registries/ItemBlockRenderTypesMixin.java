package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import org.sinytra.connector.mod.ConnectorMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemBlockRenderTypes.class)
public abstract class ItemBlockRenderTypesMixin {
    /**
     * Allow mods to register directly to {@link ItemBlockRenderTypes} after client load.
     */
    @Inject(method = "checkClientLoading", at = @At(value = "HEAD"), cancellable = true)
    private static void bypassClientLoadingCheck(CallbackInfo ci) {
        if (ConnectorMod.isClientLoadComplete()) {
            ci.cancel();
        }
    }
}
