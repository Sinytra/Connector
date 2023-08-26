package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@Mixin(PoiTypes.class)
public class PoiTypesMixin {
    @Shadow
    @Final
    private static Map<BlockState, PoiType> TYPE_BY_STATE;

    /**
     * Fabric mods may inject their own PoiTypes in to {@link PoiTypes#TYPE_BY_STATE}, expecting the map to contain values of type {@link Holder}.
     * Normally, this results in a {@link ClassCastException} in the original method when it tries to cast {@link PoiType} to a {@link Holder}.
     * To fix this, we check the type and return it if it's already a {@link Holder}.
     */
    @Inject(method = "forState", at = @At("HEAD"), cancellable = true)
    private static void handleLegacyTypeHolders(BlockState state, CallbackInfoReturnable<Optional<Holder<PoiType>>> cir) {
        Object obj = TYPE_BY_STATE.get(state);
        if (obj instanceof Holder<?> holder) {
            cir.setReturnValue(Optional.of((Holder<PoiType>) holder));
        }
    }
}
