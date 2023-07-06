package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.GameData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applied on dedicated servers only
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Unique
    private static final Logger CONNECTOR_LOGGER = LoggerFactory.getLogger(MinecraftServerMixin.class);

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"))
    private void beforeSetupServer(CallbackInfo info) {
        // Lock registries on the server
        CONNECTOR_LOGGER.debug("Locking registries");
        GameData.vanillaSnapshot();
    }
}
