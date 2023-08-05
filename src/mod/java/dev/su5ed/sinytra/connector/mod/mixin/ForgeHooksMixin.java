package dev.su5ed.sinytra.connector.mod.mixin;

import dev.su5ed.sinytra.connector.mod.ConnectorLoader;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgeHooks.class)
public abstract class ForgeHooksMixin {

    @Inject(method = "getVanillaFluidType", at = @At(value = "NEW", target = "java/lang/RuntimeException"), remap = false, cancellable = true)
    private static void getFabricVanillaFluidType(Fluid fluid, CallbackInfoReturnable<FluidType> cir) {
        ForgeRegistries.FLUIDS.getResourceKey(fluid)
            .flatMap(key -> ModList.get().getModContainerById(key.location().getNamespace()))
            .filter(container -> ConnectorLoader.isConnectorMod(container.getModInfo().getModId()))
            .ifPresent(container -> cir.setReturnValue(ForgeMod.EMPTY_TYPE.get()));
    }
}
