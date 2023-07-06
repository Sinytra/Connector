package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.fml.common.Mod;

@Mod("connectormod")
public class ConnectorMod {

    public ConnectorMod() {
        Throwable loadingException = ConnectorEarlyLoader.getLoadingException();
        if (loadingException != null) {
            throw new RuntimeException("Connector early loading failed", loadingException);
        }
    }
}
