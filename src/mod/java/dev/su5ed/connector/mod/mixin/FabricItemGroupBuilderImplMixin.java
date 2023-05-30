package dev.su5ed.connector.mod.mixin;

import dev.su5ed.connector.mod.FabricItemGroups;
import net.fabricmc.fabric.impl.itemgroup.FabricItemGroupBuilderImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings("unused")
@Mixin(FabricItemGroupBuilderImpl.class)
public abstract class FabricItemGroupBuilderImplMixin {
    @Shadow
    @Final
    private ResourceLocation identifier;

    @Redirect(method = "build", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/itemgroup/ItemGroupHelper;appendItemGroup(Lnet/minecraft/world/item/CreativeModeTab;)V"))
    private void appendItemGroup(CreativeModeTab tab) {
        FabricItemGroups.register(this.identifier, tab);
    }
}
