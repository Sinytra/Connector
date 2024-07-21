package org.sinytra.connector.mod.mixin.boot;

import net.neoforged.neoforge.data.loading.DatagenModLoader;
import org.sinytra.connector.mod.ConnectorLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DatagenModLoader.class, remap = false)
public abstract class DatagenModLoaderMixin {
    @Inject(method = "begin", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/data/loading/DatagenModLoader;begin(Ljava/lang/Runnable;Z)V"))
    private static void earlyInit(CallbackInfo ci) {
        ConnectorLoader.load();
    }
}
