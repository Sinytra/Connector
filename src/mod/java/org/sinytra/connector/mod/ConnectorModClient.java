package org.sinytra.connector.mod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.sinytra.connector.mod.compat.LateRenderTypesInit;
import org.sinytra.connector.mod.compat.LateSheetsInit;
import org.sinytra.connector.util.ConnectorUtil;

@Mod(value = ConnectorUtil.CONNECTOR_MODID, dist = Dist.CLIENT)
public class ConnectorModClient {

    public ConnectorModClient(IEventBus bus) {
        bus.addListener(ConnectorModClient::onClientSetup);
        bus.addListener(ConnectorModClient::onLoadComplete);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LateRenderTypesInit.regenerateRenderTypeIds();
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        LateSheetsInit.completeSheetsInit();
    }
}
