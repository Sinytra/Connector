package org.sinytra.connector.mod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Inject(method = "getPlayers", at = @At("HEAD"), cancellable = true)
    private void onGetPlayers(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        // Ensure returned view is up-to-date
        // Mods may replace the value of players, invalidating the view initially created by Forge
        cir.setReturnValue(Collections.unmodifiableList(players));
    }
}
