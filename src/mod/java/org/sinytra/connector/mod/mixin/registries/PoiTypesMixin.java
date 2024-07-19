package org.sinytra.connector.mod.mixin.registries;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.sinytra.connector.mod.ConnectorLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(PoiTypes.class)
public abstract class PoiTypesMixin {

    // Some mods call PoiTypes.register directly, resulting in duplicate registration 
    // https://github.com/quiqueck/BCLib/blob/53349085e5d3adb20a023f29d9aab85acce58332/src/main/java/org/betterx/bclib/api/v2/poi/PoiManager.java#L27
    @WrapOperation(method = "register", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiTypes;registerBlockStates(Lnet/minecraft/core/Holder;Ljava/util/Set;)V"))
    private static void preventDuplicateRegistration(Holder<PoiType> poi, Set<BlockState> states, Operation<Void> original) {
        if (ConnectorLoader.isLoading()) {
            return;
        }
        original.call(poi, states);
    }
}
