package org.sinytra.connector.mod.mixin.registries;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mitigate vanilla namespace usage in custom payload type
// https://github.com/quiqueck/WunderLib/blob/50cf417626a094925426ad53cb2150ddea47365b/src/main/java/de/ambertation/wunderlib/network/PacketHandler.java#L21
@Mixin(NetworkRegistry.class)
public abstract class NetworkRegistryMixin {
    @Shadow
    @Final
    private static Map<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> PAYLOAD_REGISTRATIONS;

    @Redirect(method = "register", at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
    private static boolean dontCollapseOnDefaultNamespace(String str, Object obj) {
        return false;
    }

    @ModifyExpressionValue(method = "isModdedPayload", at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
    private static boolean allowMinecraftModdedPayload(boolean result, CustomPacketPayload payload) {
        if (result) {
            for (Map.Entry<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> entry : PAYLOAD_REGISTRATIONS.entrySet()) {
                if (entry.getValue().containsKey(payload.type().id())) {
                    return true;
                }
            }
        }
        return result;
    }

    @Inject(
        method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onCheckPacketServer(Packet<?> packet, ServerCommonPacketListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket) {
            ResourceLocation id = customPayloadPacket.payload().type().id();
            if (ConnectorEarlyLoader.isConnectorMod(id.getNamespace())
                || ConnectorEarlyLoader.isConnectorModClass(customPayloadPacket.payload().getClass())
            ) {
                ci.cancel();
            }
        }
    }

    @Inject(
        method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onCheckPacketClient(Packet<?> packet, ClientCommonPacketListener listener, CallbackInfo ci) {
        if (packet instanceof ServerboundCustomPayloadPacket customPayloadPacket) {
            ResourceLocation id = customPayloadPacket.payload().type().id();
            if (ConnectorEarlyLoader.isConnectorMod(id.getNamespace()) 
                || ConnectorEarlyLoader.isConnectorModClass(customPayloadPacket.payload().getClass())
            ) {
                ci.cancel();
            }
        }
    }
}
