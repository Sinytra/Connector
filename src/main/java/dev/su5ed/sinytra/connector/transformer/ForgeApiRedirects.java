package dev.su5ed.sinytra.connector.transformer;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;

import java.util.Set;

public final class ForgeApiRedirects {
    private static volatile IMappingFile mappings;

    public static IMappingFile getMappings() {
        if (mappings == null) {
            synchronized (ForgeApiRedirects.class) {
                if (mappings == null) {
                    MappingResolverImpl resolver = FabricLoaderImpl.INSTANCE.getMappingResolver();
                    IMappingFile map = resolver.getMap("srg", "intermediary");
                    IMappingBuilder builder = IMappingBuilder.create();

                    Set.of("net/fabricmc/fabric/api/item/v1/FabricItem", map.remapClass("net/minecraft/world/item/Item"))
                        .forEach(name -> {
                            IMappingBuilder.IClass cls = builder.addClass(name, name);
                            cls.method(map.remapDescriptor("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"), "getAttributeModifiers", "connector_getAttributeModifiers");
                            cls.method(map.remapDescriptor("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z"), "isSuitableFor", "isCorrectToolForDrops");
                            cls.method(map.remapDescriptor("(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"), "getRecipeRemainder", "getCraftingRemainingItem");
                            cls.method(map.remapDescriptor("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), "allowContinuingBlockBreaking", "connector_allowContinuingBlockBreaking");
                            cls.method(map.remapDescriptor("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), "allowNbtUpdateAnimation", "connector_allowNbtUpdateAnimation");
                        });

                    mappings = builder.build().getMap("left", "right");
                }
            }
        }
        return mappings;
    }

    private ForgeApiRedirects() {}
}
