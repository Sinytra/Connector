package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@SuppressWarnings("unused")
public abstract class EntityInteractionApiReplacements {

    // TODO Automatic LVT patch
    @Inject(
        at = {@At(
            value = "INVOKE",
            target = "net/minecraft/client/network/ClientPlayerInteractionManager.interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"
        )},
        method = {"doItemUse"},
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectUseEntityCallback(CallbackInfo ci, InteractionHand[] hands, int i1, int i2, InteractionHand hand, InputEvent.InteractionKeyMappingTriggered event, ItemStack stack, EntityHitResult hitResult, Entity entity) {
        Minecraft self = (Minecraft) (Object) this;
        InteractionResult result = UseEntityCallback.EVENT.invoker().interact(self.player, self.player.getCommandSenderWorld(), hand, entity, hitResult);
        if (result != InteractionResult.PASS) {
            if (result.consumesAction()) {
                Vec3 hitVec = hitResult.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
                self.getConnection().send(ServerboundInteractPacket.createInteractionPacket(entity, self.player.isShiftKeyDown(), hand, hitVec));
            }

            if (result.shouldSwing()) {
                self.player.swing(hand);
            }

            ci.cancel();
        }
    }
}
