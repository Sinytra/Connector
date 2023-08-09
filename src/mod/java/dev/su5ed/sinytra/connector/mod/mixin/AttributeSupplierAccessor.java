package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(AttributeSupplier.class)
public interface AttributeSupplierAccessor {
    @Accessor
    Map<Attribute, AttributeInstance> getInstances();

    @Accessor
    @Mutable
    void setInstances(Map<Attribute, AttributeInstance> instances);
}
