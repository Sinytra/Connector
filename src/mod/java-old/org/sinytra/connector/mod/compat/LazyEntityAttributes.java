package org.sinytra.connector.mod.compat;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.RegistryObject;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class LazyEntityAttributes {
    private static final MethodHandle REGISTRY_OBJECT_SET_VALUE = LamdbaExceptionUtils.uncheck(() -> MethodHandles.privateLookupIn(RegistryObject.class, MethodHandles.lookup()).findSetter(RegistryObject.class, "value", Object.class));
    private static final List<RegistryObject<Attribute>> ATTRIBUTES = List.of(ForgeMod.SWIM_SPEED, ForgeMod.NAMETAG_DISTANCE, ForgeMod.ENTITY_GRAVITY, ForgeMod.STEP_HEIGHT_ADDITION);
    private static final Map<Supplier<? extends Attribute>, PlaceholderAttribute> PLACEHOLDERS = new HashMap<>();

    public static void inject() {
        for (RegistryObject<Attribute> registryObject : ATTRIBUTES) {
            Attribute lazyAttribute = PLACEHOLDERS.computeIfAbsent(registryObject, s -> new PlaceholderAttribute());
            try {
                REGISTRY_OBJECT_SET_VALUE.invoke(registryObject, lazyAttribute);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void release() {
        for (RegistryObject<Attribute> registryObject : ATTRIBUTES) {
            try {
                REGISTRY_OBJECT_SET_VALUE.invoke(registryObject, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
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
                        value.instances = instances;
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
