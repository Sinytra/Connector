package org.sinytra.connector.mod.mixin.network;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraftforge.network.filters.NetworkFilters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(NetworkFilters.class)
public class NetworkFiltersMixin {

    @Inject(method = "lambda$injectIfNecessary$1", at = @At(value = "INVOKE", target = "Lio/netty/channel/ChannelPipeline;addBefore(Ljava/lang/String;Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;"), remap = false, cancellable = true)
    private static void preventDuplicateHandler(Connection manager, ChannelPipeline pipeline, String key, Function filterFactory, CallbackInfo ci) {
        if (pipeline.get(key) != null) {
            ci.cancel();
        }
    }
}
