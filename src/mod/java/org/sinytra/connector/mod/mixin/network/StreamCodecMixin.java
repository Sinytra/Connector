package org.sinytra.connector.mod.mixin.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GenericPacketSplitter runs the encoder to figure out whether the packet needs splitting
// Unfortunately, this breaks Fabric mod codecs that encode buffers, which need to have their
// reader index reset before encoding
@Mixin(targets = "net.minecraft.network.codec.StreamCodec$1")
public abstract class StreamCodecMixin {

    @Inject(method = "encode(Ljava/lang/Object;Ljava/lang/Object;)V", at = @At("HEAD"))
    private void resetBufferOnEncode(Object buffer, Object value, CallbackInfo ci) {
        if (value instanceof RegistryFriendlyByteBuf buf) {
            buf.resetReaderIndex();
        }
    }
}
