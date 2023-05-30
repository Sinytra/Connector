package dev.su5ed.connector.mod.mixin;

import net.fabricmc.fabric.api.itemgroup.v1.IdentifiableItemGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin implements IdentifiableItemGroup {
    @Override
    public ResourceLocation getId() {
        return CreativeModeTabRegistry.getName((CreativeModeTab) (Object) this);
    }
}
