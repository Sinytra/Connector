package dev.su5ed.sinytra.connector.language;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.util.List;

import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class ConnectorModContainer extends ModContainer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<Object> modInstances;

    static {
        try {
            Class<?> cls = Class.forName("dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader");
            ConnectorModContainer.class.getModule().addReads(cls.getModule());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public ConnectorModContainer(IModInfo info) {
        super(info);

        LOGGER.debug(LOADING, "Creating ConnectorModContainer for {}", info.getModId());
        this.contextExtension = () -> null;
        this.modInstances = List.copyOf(ConnectorEarlyLoader.getModInstances(info.getModId()));
    }

    @Override
    public boolean matches(Object mod) {
        return this.modInstances.contains(mod);
    }

    @Override
    public Object getMod() {
        return !this.modInstances.isEmpty() ? this.modInstances.get(0) : null;
    }
}
