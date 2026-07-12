package org.sinytra.connector.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin {
    // Fixes issue in Flashback mod (needed for dev envs only)
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/contents/TranslatableContents;isAllowedPrimitiveArgument(Ljava/lang/Object;)Z"))
    private static boolean allowAnyArgument(boolean original) {
        return true;
    }
}
