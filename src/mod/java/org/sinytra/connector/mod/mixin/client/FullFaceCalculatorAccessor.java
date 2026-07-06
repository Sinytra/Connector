package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.renderer.block.BlockModelLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.neoforged.neoforge.client.model.ao.FullFaceCalculator")
public interface FullFaceCalculatorAccessor {
    @Accessor
    BlockModelLighter.Cache getCache();
}
