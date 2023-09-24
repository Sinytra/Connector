package dev.su5ed.sinytra.connector.mod.mixin;

import dev.su5ed.sinytra.connector.mod.ConnectorLoader;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Redirect(method = "createLivingAttributes", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/RegistryObject;get()Ljava/lang/Object;"))
    private static <T> T injectCreateLivingAttributes(RegistryObject<T> instance) {
        if (ConnectorLoader.isLoading()) {
            RegistryObject<RangedAttribute> coerced = (RegistryObject<RangedAttribute>) instance;
            return (T) LazyEntityAttributes.getLazyAttribute(coerced);
        }
        return instance.get();
    }
}
