package dev.su5ed.sinytra.connector.mod.compat;

import dev.su5ed.sinytra.connector.mod.mixin.AttributeSupplierAccessor;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LazyEntityAttributes {
    private static final List<AttributeSupplier> SUPPLIERS = new ArrayList<>();

    public static void addMissingAttributes(EntityAttributeCreationEvent event) {
        for (AttributeSupplier supplier : SUPPLIERS) {
            // Copy builder
            AttributeSupplier.Builder newBuilder = new AttributeSupplier.Builder(supplier);
            // Add forge attributes now that they're registered
            newBuilder
                .add(ForgeMod.SWIM_SPEED.get())
                .add(ForgeMod.NAMETAG_DISTANCE.get())
                .add(ForgeMod.ENTITY_GRAVITY.get())
                .add(ForgeMod.STEP_HEIGHT_ADDITION.get());
            // Freeze builder map
            AttributeSupplier newSupplier = newBuilder.build();
            // Get instance map
            Map<Attribute, AttributeInstance> instances = ((AttributeSupplierAccessor) newSupplier).getInstances();
            // Move expanded instance map to previous instance
            ((AttributeSupplierAccessor) supplier).setInstances(instances);
        }
    }

    public static LazyAttributeSupplierBuilder wrapBuilder(AttributeSupplier.Builder builder) {
        return new LazyAttributeSupplierBuilder(builder);
    }

    public static class LazyAttributeSupplierBuilder extends AttributeSupplier.Builder {
        private final AttributeSupplier.Builder wrapped;

        public LazyAttributeSupplierBuilder(AttributeSupplier.Builder wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void combine(AttributeSupplier.Builder other) {
            this.wrapped.combine(other);
        }

        @Override
        public boolean hasAttribute(Attribute attribute) {
            return this.wrapped.hasAttribute(attribute);
        }

        @Override
        public AttributeSupplier.Builder add(Attribute attribute) {
            this.wrapped.add(attribute);
            return this;
        }

        @Override
        public AttributeSupplier.Builder add(Attribute attribute, double value) {
            this.wrapped.add(attribute, value);
            return this;
        }

        @Override
        public AttributeSupplier build() {
            AttributeSupplier supplier = this.wrapped.build();
            SUPPLIERS.add(supplier);
            return supplier;
        }
    }
}
