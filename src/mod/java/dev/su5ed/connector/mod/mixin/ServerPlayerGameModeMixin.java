package dev.su5ed.connector.mod.mixin;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;
    @Final
    @Shadow
    protected ServerPlayer player;

    @Inject(
        method = {"removeBlock"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onBlockBroken(BlockPos pos, boolean canHarvest, CallbackInfoReturnable<?> cir, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(this.level, this.player, pos, state, entity);
    }
}
