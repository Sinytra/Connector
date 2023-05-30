package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions;
import net.fabricmc.fabric.impl.networking.client.ClientLoginNetworkAddon;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraftforge.network.ICustomPacket;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings("unused")
public abstract class NetworkingApiReplacements {

    @Redirect(
        method = {"onQueryRequest"},
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/NetworkHooks;onCustomPayload(Lnet/minecraftforge/network/ICustomPacket;Lnet/minecraft/network/Connection;)Z")
    )
    private boolean handleQueryRequest(ICustomPacket<?> packet, final Connection manager) {
        ClientLoginNetworkAddon addon = (ClientLoginNetworkAddon) ((NetworkHandlerExtensions) this).getAddon();
        return NetworkHooks.onCustomPayload(packet, manager) && addon.handlePacket((ClientboundCustomQueryPacket) packet);
    }
}
