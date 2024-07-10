package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.sinytra.connector.mod.compat.fieldtypes.FieldTypeUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {
    @Shadow
    @Final
    private Map<Holder.Reference<Block>, BlockColor> blockColors;

    @Unique
    private IdMapper<BlockColor> connector$blockColors;

    @Unique
    public IdMapper<BlockColor> connector$getBlockColors() {
        if (this.connector$blockColors == null) {
            this.connector$blockColors = FieldTypeUtil.createRedirectingMapperSafely(
                id -> BuiltInRegistries.BLOCK.getHolder(id).orElseThrow(),
                blockReference -> BuiltInRegistries.BLOCK.getId(blockReference.value()),
                this.blockColors
            );
        }
        return this.connector$blockColors;
    }
}
