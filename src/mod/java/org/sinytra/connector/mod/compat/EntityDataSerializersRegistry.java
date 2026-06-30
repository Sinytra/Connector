package org.sinytra.connector.mod.compat;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.sinytra.connector.api.Constants;
import org.sinytra.connector.mod.mixin.registries.MappedRegistryAccessor;

public class EntityDataSerializersRegistry {
    private static int counter = 0;

    public static void register(EntityDataSerializer<?> serializer) {
        boolean frozen = ((MappedRegistryAccessor) NeoForgeRegistries.ENTITY_DATA_SERIALIZERS).getFrozen();
        ((MappedRegistry<EntityDataSerializer<?>>) NeoForgeRegistries.ENTITY_DATA_SERIALIZERS).unfreeze(false);

        Identifier name = Identifier.fromNamespaceAndPath(Constants.CONNECTOR_MODID, "entity_data_serializer" + counter++);
        Registry.register(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, name, serializer);

        if (frozen) {
            NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.freeze();
        }
    }
}
