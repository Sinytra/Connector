package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings("unused")
public abstract class EntityApiReplacements {
    @Redirect(
        method = {"lambda$stopSleeping$11", "startSleeping"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"
        )
    )
    @Dynamic("lambda$stopSleeping$11: Synthetic lambda body for Optional.ifPresent in stopSleeping")
    private void setOccupiedState(BlockState pState, Level pLevel, BlockPos pPos, LivingEntity pSleeper, boolean pOccupied) {
        BlockState originalState = pLevel.getBlockState(pPos);
        pState.setBedOccupied(pLevel, pPos, pSleeper, false);
        EntitySleepEvents.SET_BED_OCCUPATION_STATE.invoker().setBedOccupationState(pSleeper, pPos, originalState, pOccupied);
    }
}
