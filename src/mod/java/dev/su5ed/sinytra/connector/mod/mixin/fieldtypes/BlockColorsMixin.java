package dev.su5ed.sinytra.connector.mod.mixin.fieldtypes;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(BlockColors.class)
public class BlockColorsMixin {
    @Shadow
    @Final
    private Map<Holder.Reference<Block>, BlockColor> blockColors;
    @Unique
    public IdMapper<BlockColor> connector$blockColors = new IdMapper<>();

    @Unique
    public IdMapper<BlockColor> connector$getBlockColors() {
        if (this.blockColors.size() != this.connector$blockColors.size()) {
            if (this.blockColors.size() < this.connector$blockColors.size()) throw new IllegalStateException("Tried to register new block colors using connector$getBlockColors()!");
            this.blockColors.forEach((block, color) -> {
                this.connector$blockColors.addMapping(color, BuiltInRegistries.BLOCK.getId(block.get()));
            });
        }
        return this.connector$blockColors;
    }
}
