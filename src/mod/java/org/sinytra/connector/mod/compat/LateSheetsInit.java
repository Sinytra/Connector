package org.sinytra.connector.mod.compat;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.WoodType;

public class LateSheetsInit {
    public static void completeSheetsInit() {
        WoodType.values().forEach(woodType -> {
            if (!Sheets.SIGN_MATERIALS.containsKey(woodType)) {
                Sheets.SIGN_MATERIALS.put(woodType, Sheets.createSignMaterial(woodType));
            }
            if (!Sheets.HANGING_SIGN_MATERIALS.containsKey(woodType)) {
                Sheets.HANGING_SIGN_MATERIALS.put(woodType, Sheets.createHangingSignMaterial(woodType));
            }
        });
        BuiltInRegistries.BANNER_PATTERN.registryKeySet().stream()
            .filter(key -> !Sheets.BANNER_MATERIALS.containsKey(key))
            .forEach(key -> Sheets.BANNER_MATERIALS.put(key, Sheets.createBannerMaterial(key)));

        BuiltInRegistries.DECORATED_POT_PATTERNS.registryKeySet().stream()
            .filter(key -> !Sheets.DECORATED_POT_MATERIALS.containsKey(key))
            .forEach(key -> Sheets.DECORATED_POT_MATERIALS.put(key, Sheets.createDecoratedPotMaterial(key)));
    }
}
