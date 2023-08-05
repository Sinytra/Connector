package dev.su5ed.sinytra.connector.mod.compat;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// IMPORTANT: This class is meant to be referenced from ASM-transformed code.
// Please check for usages in patch definition classes before renaming it.
public final class LateEntityAttributeRegistry {
    private static final List<Pair<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier>>> ATTRIBUTES = new ArrayList<>();

    // Called from patched methods
    @SuppressWarnings("unused")
    public static void registerBuilder(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier.Builder> builder) {
        ATTRIBUTES.add(Pair.of(type, () -> builder.get().build()));
    }

    // Called from patched methods
    @SuppressWarnings("unused")
    public static void register(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier> builder) {
        ATTRIBUTES.add(Pair.of(type, builder));
    }

    // Highest priority listener
    public static void onEntityAttributesCreate(EntityAttributeCreationEvent event) {
        for (Pair<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier>> pair : ATTRIBUTES) {
            FabricDefaultAttributeRegistry.register(pair.getFirst(), pair.getSecond().get());
        }
    }

    private LateEntityAttributeRegistry() {}
}
