package org.sinytra.connector.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.fluids.FluidType;
import org.sinytra.connector.mod.ConnectorMod;
import org.sinytra.connector.mod.compat.FluidHandlerCompat;
import org.sinytra.connector.mod.compat.ItemStackExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(CommonHooks.class)
public abstract class CommonHooksMixin {

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
            AttributeSupplier fromVanilla = DefaultAttributes.getSupplier(entity);
            // A mod is mixing into DefaultAttributes to add their attribute
            if (CommonHooks.getAttributesView().get(entity) != fromVanilla) {
                ConnectorMod.LOGGER.debug("Entity {} has its attributes added via a mixin. Adding event-modified attributes.", entity);
                AttributeSupplier.Builder newBuilder = new AttributeSupplier.Builder(fromVanilla);
                newBuilder.combine(attributes);
                fromVanilla.instances = newBuilder.build().instances;
            }
        });
    }

    @Inject(method = "onPlaceItemIntoWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private static void appyUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = context.getItemInHand();
        InteractionResult result = ((ItemStackExtensions) (Object) stack).connector_useOn(context);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
