package dev.su5ed.sinytra.connector.mod.mixin.registries;

import net.minecraft.client.Minecraft;
import net.minecraftforge.registries.GameData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private static final Logger CONNECTOR_LOGGER = LoggerFactory.getLogger(MinecraftMixin.class);

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0), method = "run")
    private void onStart(CallbackInfo ci) {
        // Lock registries on the client
        CONNECTOR_LOGGER.debug("Locking registries");
        GameData.vanillaSnapshot();
    }
}
