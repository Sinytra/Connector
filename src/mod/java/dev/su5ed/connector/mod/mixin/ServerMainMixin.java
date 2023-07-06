package dev.su5ed.connector.mod.mixin;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class ServerMainMixin {

    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/server/loading/ServerModLoader;load()V"), remap = false)
    private static void earlyInit(CallbackInfo ci) {
        ConnectorEarlyLoader.load();
    }
}
