package dev.su5ed.connector.mod;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@SuppressWarnings("unused")
public final class EntityInteractionApiEvents {

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        BlockEntity be = level.getBlockEntity(event.getPos());
        boolean result = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(), be);
        if (!result) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, event.getPlayer(), event.getPos(), event.getState(), be);
            event.setCanceled(true);
        }
    }
}
