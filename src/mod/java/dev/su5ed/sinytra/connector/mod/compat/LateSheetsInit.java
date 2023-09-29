package dev.su5ed.sinytra.connector.mod.compat;

import dev.su5ed.sinytra.connector.mod.mixin.SheetsAccessor;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.level.block.state.properties.WoodType;

public class LateSheetsInit {
    public static void completeSheetsInit() {
        WoodType.values().forEach(woodType -> {
            if (!Sheets.SIGN_MATERIALS.containsKey(woodType)) {
                Sheets.SIGN_MATERIALS.put(woodType, SheetsAccessor.callCreateSignMaterial(woodType));
            }
            if (!Sheets.HANGING_SIGN_MATERIALS.containsKey(woodType)) {
                Sheets.HANGING_SIGN_MATERIALS.put(woodType, SheetsAccessor.callCreateHangingSignMaterial(woodType));
            }
        });
    }
}
