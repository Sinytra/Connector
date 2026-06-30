package org.sinytra.connector.mod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.sinytra.connector.api.Constants;
import org.sinytra.connector.mod.compat.LateSheetsInit;

@Mod(value = Constants.CONNECTOR_MODID, dist = Dist.CLIENT)
public class ConnectorModClient {

    public ConnectorModClient(IEventBus bus) {
        bus.addListener(ConnectorModClient::onLoadComplete);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        LateSheetsInit.completeSheetsInit();
    }
}
