package dev.su5ed.sinytra.connector.mod.mixin;

import dev.su5ed.sinytra.connector.mod.ConnectorLoader;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "createLivingAttributes", at = @At("HEAD"), cancellable = true)
    private static void injectCreateLivingAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        if (ConnectorLoader.isLoading()) {
            AttributeSupplier.Builder builder = AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE)
                .add(Attributes.MOVEMENT_SPEED)
                .add(Attributes.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS);
            cir.setReturnValue(LazyEntityAttributes.wrapBuilder(builder));
        }
    }
}
