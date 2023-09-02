package dev.su5ed.sinytra.connector.mod.compat;

import dev.su5ed.sinytra.connector.mod.mixin.AttributeSupplierAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class LazyEntityAttributes {
    private static final Map<Supplier<? extends Attribute>, PlaceholderAttribute> PLACEHOLDERS = new HashMap<>();

    public static Attribute getLazyAttribute(Supplier<? extends Attribute> original) {
        return PLACEHOLDERS.computeIfAbsent(original, s -> new PlaceholderAttribute());
    }

    private static class PlaceholderAttribute extends Attribute {
        public PlaceholderAttribute() {
            super("connector_placeholder_attribute", 0);
        }
    }

    public static void initializeLazyAttributes(EntityAttributeModificationEvent event) {
        updateAttributeSuppliers(ObfuscationReflectionHelper.getPrivateValue(DefaultAttributes.class, null, ASMAPI.mapField("f_22294_")));
        updateAttributeSuppliers(ForgeHooks.getAttributesView());
    }

    private static void updateAttributeSuppliers(Map<EntityType<? extends LivingEntity>, AttributeSupplier> map) {
        map.forEach((entityType, value) -> {
            AtomicBoolean madeMutable = new AtomicBoolean(false);
            PLACEHOLDERS.forEach((originalSupplier, placeholder) -> {
                if (value.hasAttribute(placeholder)) {
                    Map<Attribute, AttributeInstance> instances = ObfuscationReflectionHelper.getPrivateValue(AttributeSupplier.class, value, ASMAPI.mapField("f_22241_"));
                    if (!madeMutable.get()) {
                        instances = new HashMap<>(instances);
                        ((AttributeSupplierAccessor) value).setInstances(instances);
                        madeMutable.set(true);
                    }
                    Attribute original = originalSupplier.get();
                    instances.remove(placeholder);
                    instances.put(original, new AttributeInstance(original, i -> {}));
                }
            });
        });
    }
}
