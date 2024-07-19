package org.sinytra.connector.mod.mixin;

import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.common.util.Lazy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Shadow
    private Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;

    // https://github.com/quiqueck/WorldWeaver/blob/8861dbf39c85cdafbaf2caab1783d11c26d78f44/wover-biome-api/src/main/java/org/betterx/wover/biome/mixin/ChunkGeneratorAccessor.java#L22
    @Inject(method = "refreshFeaturesPerStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;featuresPerStep:Ljava/util/function/Supplier;"), cancellable = true)
    private void fixBrokenCast(CallbackInfo ci) {
        if (!(this.featuresPerStep instanceof Lazy<?>)) {
            ci.cancel();
        }
    }
}
