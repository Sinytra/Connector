package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Sheets.class)
public interface SheetsAccessor {
    @Invoker
    static Material callCreateSignMaterial(WoodType woodType) {
        throw new UnsupportedOperationException();
    }

    @Invoker
    static Material callCreateHangingSignMaterial(WoodType woodType) {
        throw new UnsupportedOperationException();
    }
}
