package dev.su5ed.connector.mod;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@SuppressWarnings("unused")
public final class ContentRegistriesApiEvents {

    // Replaces net.fabricmc.fabric.mixin.content.registry.AbstractFurnaceBlockEntityMixin canUseAsFuelRedirect
    // Replaces net.fabricmc.fabric.mixin.content.registry.AbstractFurnaceBlockEntityMixin getFuelTimeRedirect
    @SubscribeEvent
    public static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        Integer burnTime = FuelRegistry.INSTANCE.get(event.getItemStack().getItem());
        if (burnTime != null) {
            event.setBurnTime(burnTime);
        }
    }
}
