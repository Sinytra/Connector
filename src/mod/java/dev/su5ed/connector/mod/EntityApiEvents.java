package dev.su5ed.connector.mod;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@SuppressWarnings("unused")
public final class EntityApiEvents {

    // net.fabricmc.fabric.mixin.entity.event.PlayerEntityMixin redirectDaySleepCheck
    @SubscribeEvent
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        Player player = event.getEntity();
        boolean day = player.level.isDay();
        if (player.getSleepingPos().isPresent()) {
            BlockPos pos = player.getSleepingPos().get();
            InteractionResult result = EntitySleepEvents.ALLOW_SLEEP_TIME.invoker().allowSleepTime(player, pos, !day);
            if (result != InteractionResult.PASS) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    private EntityApiEvents() {}
}
