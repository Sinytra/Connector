package org.sinytra.connector.mod.compat;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.WoodType;

public class LateSheetsInit {
    public static void completeSheetsInit() {
        WoodType.values().forEach(woodType -> {
            if (!Sheets.SIGN_SPRITES.containsKey(woodType)) {
                Sheets.SIGN_SPRITES.put(woodType, Sheets.createSignSprite(woodType));
            }
            if (!Sheets.HANGING_SIGN_SPRITES.containsKey(woodType)) {
                Sheets.HANGING_SIGN_SPRITES.put(woodType, Sheets.createHangingSignSprite(woodType));
            }
        });

        BuiltInRegistries.DECORATED_POT_PATTERN.entrySet().stream()
            .filter(entry -> !Sheets.DECORATED_POT_SPRITES.containsKey(entry.getKey()))
            .forEach(entry -> Sheets.DECORATED_POT_SPRITES.put(entry.getKey(), Sheets.DECORATED_POT_MAPPER.apply(entry.getValue().assetId())));
    }
}
