package org.sinytra.connector.mod;

import com.electronwill.nightconfig.core.file.FileConfigBuilder;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.sinytra.connector.api.Constants;
import org.sinytra.connector.mod.compat.FluidHandlerCompat;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URL;

@Mod(Constants.CONNECTOR_MODID)
public class ConnectorMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public ConnectorMod(IEventBus bus) {
//        bus.addListener(FluidHandlerCompatClient::onRegisterClientExtensions); TODO
        
        if (FabricLoader.getInstance().isModLoaded("fabric-transfer-api-v1")) {
            FluidHandlerCompat.init(bus);
        }
    }

    // Injected into mod code by ClassAnalysingTransformer
    @SuppressWarnings("unused")
    public static InputStream getModResourceAsStream(Class<?> clazz, String name) {
        InputStream classRes = clazz.getResourceAsStream(name);
        return classRes != null ? classRes : clazz.getClassLoader().getResourceAsStream(name);
    }

    // Injected into mod code by ClassAnalysingTransformer
    @SuppressWarnings("unused")
    public static GenericBuilder<?, ?> useModConfigResource(FileConfigBuilder builder, String resource) {
        URL url = ConnectorMod.class.getClassLoader().getResource(resource);
        return builder.onFileNotFound(FileNotFoundAction.copyData(url));
    }

    public static String getVersion() {
        return ConnectorMixinPlugin.class.getModule().getDescriptor().rawVersion().orElse("<unknown>");
    }
}
