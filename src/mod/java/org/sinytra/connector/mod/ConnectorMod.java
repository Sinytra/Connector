package org.sinytra.connector.mod;

import com.electronwill.nightconfig.core.file.FileConfigBuilder;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.sinytra.connector.mod.compat.FluidHandlerCompat;
import org.sinytra.connector.mod.compat.FluidHandlerCompatClient;
import org.sinytra.connector.mod.compat.LazyEntityAttributes;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URL;

@Mod(ConnectorUtil.CONNECTOR_MODID)
public class ConnectorMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean clientLoadComplete;

    public ConnectorMod(IEventBus bus) {
        ModList modList = ModList.get();

        bus.addListener(ConnectorMod::onClientSetup);
        bus.addListener(FluidHandlerCompatClient::onRegisterClientExtensions);
        FluidHandlerCompat.init(bus);

        if (modList.isLoaded("fabric_object_builder_api_v1")) {
            bus.addListener(EventPriority.HIGHEST, LazyEntityAttributes::initializeLazyAttributes);
        }
    }

    public static boolean isClientLoadComplete() {
        return clientLoadComplete;
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        clientLoadComplete = true;
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
}
