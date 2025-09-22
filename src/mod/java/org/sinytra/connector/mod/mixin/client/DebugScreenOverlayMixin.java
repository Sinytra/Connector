package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.sinytra.connector.mod.ConnectorMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void getLeftText(CallbackInfoReturnable<List<String>> info) {
        info.getReturnValue().add("Sinytra Connector v" + ConnectorMod.getVersion());
    }
}
