package org.sinytra.connector.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.sinytra.connector.mod.ConnectorMod;
import org.sinytra.connector.mod.compat.FluidHandlerCompat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ForgeHooks.class)
public abstract class ForgeHooksMixin {

    @Inject(method = "getVanillaFluidType", at = @At(value = "NEW", target = "java/lang/RuntimeException"), remap = false, cancellable = true)
    private static void getFabricVanillaFluidType(Fluid fluid, CallbackInfoReturnable<FluidType> cir) {
        FluidType fabricFluidType = FluidHandlerCompat.getFabricFluidType(fluid);
        if (fabricFluidType != null) {
            cir.setReturnValue(fabricFluidType);
        }
    }

    @Inject(at = @At("TAIL"), method = "modifyAttributes", remap = false)
    private static void connector$allowAttributeMixins(CallbackInfo ci, @Local Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> modifiedMap) {
        modifiedMap.forEach((entity, attributes) -> {
            final var fromVanilla = DefaultAttributes.getSupplier(entity);
            // A mod is mixing into DefaultAttributes to add their attribute
            if (ForgeHooks.getAttributesView().get(entity) != fromVanilla) {
                ConnectorMod.LOG.debug("Entity {} has its attributes added via a mixin. Adding event-modified attributes.", entity);
                final AttributeSupplier.Builder newBuilder = new AttributeSupplier.Builder(fromVanilla);
                newBuilder.combine(attributes);
                fromVanilla.instances = newBuilder.build().instances;
            }
        });
    }
}
