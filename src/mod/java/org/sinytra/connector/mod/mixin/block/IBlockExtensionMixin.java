package org.sinytra.connector.mod.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IBlockExtension.class, priority = 500)
public interface IBlockExtensionMixin {
    @Inject(at = @At("HEAD"), method = "getBeaconColorMultiplier")
    private void textureColorInjectionPoint(BlockState state, LevelReader levelReader, BlockPos pos, BlockPos beaconPos, CallbackInfoReturnable<Integer> cir) {
        // This mixin adds both an injection target with swapped block pos parameters (to match the vanilla local order)
        // and a safe cast around the LevelReader as the Neo extension downgrades the type from Level
        if (this instanceof BeaconBeamBlock bb && levelReader instanceof Level level) {
            var oldColor = bb.getColor().getTextureDiffuseColor();
            var newColor = connector_getTextureDiffuseColor(bb.getColor(), level, beaconPos, pos);
            // For compatibility, we only override the color if any retargeted mixin changed it
            if (oldColor != newColor) {
                cir.setReturnValue(newColor);
            }
        }
    }

    private int connector_getTextureDiffuseColor(DyeColor color, Level level, BlockPos beaconPos, BlockPos blockPos) {
        return color.getTextureDiffuseColor();
    }
}
