package dev.su5ed.connector.replacement;

import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.Optional;

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

    // TODO parameters replacement
    @Inject(
        method = {"startSleepInBed"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
            shift = At.Shift.BY,
            by = 3
        )},
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onTrySleepDirectionCheck(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> info, Optional<BlockPos> opt, Player.BedSleepingProblem problem, @Nullable Direction sleepingDirection) {
        if (sleepingDirection == null) {
            info.setReturnValue(Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE));
        }
    }
}
