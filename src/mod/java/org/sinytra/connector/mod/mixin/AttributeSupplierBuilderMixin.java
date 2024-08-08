package org.sinytra.connector.mod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.sinytra.connector.mod.ConnectorLoader;
import org.sinytra.connector.mod.compat.LazyEntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AttributeSupplier.Builder.class)
public abstract class AttributeSupplierBuilderMixin {

    // Mitigates https://github.com/Sinytra/Connector/issues/1298
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private Holder<Attribute> onCreate(Holder<Attribute> original) {
        if (ConnectorLoader.isLoading()) {
            try {
                original.value();
            } catch (NullPointerException n) {
                // Likely a deferred holder that has not been initialized yet
                return LazyEntityAttributes.replaceAttribute(original);
            }
        }
        return original;
    }
}
