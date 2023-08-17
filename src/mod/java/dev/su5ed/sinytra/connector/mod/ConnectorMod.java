package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.mod.compat.FluidHandlerCompat;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import dev.su5ed.sinytra.connector.mod.compat.ModMenuCompat;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod("connectormod")
public class ConnectorMod {
    private static boolean clientLoadComplete;

    public static boolean clientLoadComplete() {
        return clientLoadComplete;
    }

    public ConnectorMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(ConnectorMod::onClientSetup);
        ModList modList = ModList.get();
        if (FMLLoader.getDist().isClient() && modList.isLoaded("fabric_rendering_fluids_v1")) {
            bus.addListener(FluidHandlerCompat::onClientSetup);
        }

        if (modList.isLoaded("fabric_object_builder_api_v1")) {
            bus.addListener(EventPriority.HIGHEST, LazyEntityAttributes::addMissingAttributes);
        }

        ModMenuCompat.init();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        clientLoadComplete = true;
    }
}
