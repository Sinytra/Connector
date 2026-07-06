package org.sinytra.connector.mod.mixin.fluid;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.fluids.FluidType;
import org.sinytra.connector.mod.compat.FluidHandlerCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommonHooks.class)
public class CommonHooksFluidMixin {
    @Inject(method = "getVanillaFluidType", at = @At(value = "NEW", target = "java/lang/RuntimeException"), remap = false, cancellable = true)
    private static void getFabricVanillaFluidType(Fluid fluid, CallbackInfoReturnable<FluidType> cir) {
        FluidType fabricFluidType = FluidHandlerCompat.getFabricFluidType(fluid);
        if (fabricFluidType != null) {
            cir.setReturnValue(fabricFluidType);
        }
    }
}
