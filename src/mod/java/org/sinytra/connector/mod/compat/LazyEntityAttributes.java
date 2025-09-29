package org.sinytra.connector.mod.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LazyEntityAttributes {
    private static final Map<Holder<Attribute>, Holder<Attribute>> PLACEHOLDERS = new HashMap<>();

    public static Holder<Attribute> replaceAttribute(Holder<Attribute> original) {
        return PLACEHOLDERS.computeIfAbsent(original, s -> Holder.direct(new PlaceholderAttribute()));
    }

    public static void initializeLazyAttributes(EntityAttributeModificationEvent event) {
        updateAttributeSuppliers(ObfuscationReflectionHelper.getPrivateValue(DefaultAttributes.class, null, "SUPPLIERS"));
        updateAttributeSuppliers(CommonHooks.getAttributesView());
    }

    private static void updateAttributeSuppliers(Map<EntityType<? extends LivingEntity>, AttributeSupplier> map) {
        map.forEach((entityType, value) -> {
            AtomicBoolean madeMutable = new AtomicBoolean(false);
            PLACEHOLDERS.forEach((originalSupplier, placeholder) -> {
                if (value.hasAttribute(placeholder)) {
                    if (!madeMutable.get()) {
                        value.instances = new HashMap<>(value.instances);
                        madeMutable.set(true);
                    }
                    value.instances.remove(placeholder);
                    value.instances.put(originalSupplier, new AttributeInstance(originalSupplier, i -> {}));
                }
            });
        });
    }

    private static class PlaceholderAttribute extends Attribute {
        public PlaceholderAttribute() {
            super("connector_placeholder_attribute", 0);
        }
    }
}
