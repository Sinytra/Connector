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
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class LazyEntityAttributes {
    private static final MethodHandle DEFERRED_HOLDER_SET_VALUE = uncheck(() -> MethodHandles.privateLookupIn(DeferredHolder.class, MethodHandles.lookup()).findSetter(DeferredHolder.class, "holder", Holder.class));
    private static final List<Holder<Attribute>> ATTRIBUTES = List.of(NeoForgeMod.SWIM_SPEED, NeoForgeMod.NAMETAG_DISTANCE);
    private static final Map<Holder<Attribute>, Holder<Attribute>> PLACEHOLDERS = new HashMap<>();

    public static void inject() {
        for (Holder<Attribute> holder : ATTRIBUTES) {
            Holder<Attribute> lazyAttribute = PLACEHOLDERS.computeIfAbsent(holder, s -> Holder.direct(new PlaceholderAttribute()));
            try {
                DEFERRED_HOLDER_SET_VALUE.invoke(holder, lazyAttribute);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void release() {
        for (Holder<Attribute> registryObject : ATTRIBUTES) {
            try {
                DEFERRED_HOLDER_SET_VALUE.invoke(registryObject, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
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
