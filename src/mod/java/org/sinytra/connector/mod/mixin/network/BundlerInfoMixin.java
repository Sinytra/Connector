package org.sinytra.connector.mod.mixin.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/network/protocol/BundlerInfo$1")
public class BundlerInfoMixin {
    // Prevent MessageToMessageEncoder#write from screaming "must produce at least one message" if the bundle is empty
    @ModifyExpressionValue(
        method = "unbundlePacket(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/Consumer;Lio/netty/channel/ChannelHandlerContext;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z")
    )
    private boolean addSplitterPackets(boolean original, Packet<?> bundlePacket) {
        return !(bundlePacket instanceof ClientboundBundlePacket) && original;
    }
}
