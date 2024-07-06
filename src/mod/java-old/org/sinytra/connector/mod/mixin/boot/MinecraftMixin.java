package org.sinytra.connector.mod.mixin.boot;

import org.sinytra.connector.mod.ConnectorLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Increase priority to run ConnectorExtras mixins first 
@Mixin(value = Minecraft.class, priority = 3000)
public class MinecraftMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"), remap = false)
    private void earlyInit(GameConfig gameConfig, CallbackInfo ci) {
        ConnectorLoader.load();
    }
}
