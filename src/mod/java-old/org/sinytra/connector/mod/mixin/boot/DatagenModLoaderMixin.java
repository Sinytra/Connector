package org.sinytra.connector.mod.mixin.boot;

import net.minecraftforge.data.loading.DatagenModLoader;
import org.sinytra.connector.mod.ConnectorLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DatagenModLoader.class, remap = false)
public class DatagenModLoaderMixin {
    @Inject(method = "begin", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;gatherAndInitializeMods(Lnet/minecraftforge/fml/ModWorkManager$DrivenExecutor;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)V"))
    private static void earlyInit(CallbackInfo ci) {
        ConnectorLoader.load();
    }
}
