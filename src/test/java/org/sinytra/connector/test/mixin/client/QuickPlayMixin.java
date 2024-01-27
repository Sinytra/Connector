package org.sinytra.connector.test.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuickPlay.class)
public class QuickPlayMixin {
    @Inject(method = "joinSingleplayerWorld", at = @At("HEAD"))
    private static void createWorld(Minecraft pMinecraft, String pLevelName, CallbackInfo ci) {
        if (!pMinecraft.getLevelSource().levelExists(pLevelName)) {
            pMinecraft.createWorldOpenFlows().createFreshLevel(pLevelName, new LevelSettings("Connector test", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions("Connector Test".hashCode(), true, true), WorldPresets::createNormalWorldDimensions);
        }
    }
}
