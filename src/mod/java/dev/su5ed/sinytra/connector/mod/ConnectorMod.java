package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod("connectormod")
public class ConnectorMod {

    public ConnectorMod() {
        Throwable loadingException = ConnectorEarlyLoader.getLoadingException();
        if (loadingException != null) {
            throw new RuntimeException("Connector early loading failed", loadingException);
        }

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        if (FMLLoader.getDist().isClient() && ModList.get().isLoaded("fabric_rendering_fluids_v1")) {
            bus.addListener(FluidHandlerCompat::onClientSetup);
        }
    }
}
