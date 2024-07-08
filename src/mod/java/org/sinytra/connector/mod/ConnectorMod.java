package org.sinytra.connector.mod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

@Mod(ConnectorUtil.CONNECTOR_MODID)
public class ConnectorMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ConnectorMod(IEventBus bus) {
        
    }
}
