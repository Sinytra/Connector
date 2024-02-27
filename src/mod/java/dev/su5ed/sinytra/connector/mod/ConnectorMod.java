package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.locator.ConnectorConfig;
import dev.su5ed.sinytra.connector.mod.compat.FluidHandlerCompat;
import dev.su5ed.sinytra.connector.mod.compat.LateRenderTypesInit;
import dev.su5ed.sinytra.connector.mod.compat.LateSheetsInit;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.InputStream;

@Mod(ConnectorUtil.CONNECTOR_MODID)
public class ConnectorMod {
    private static boolean clientLoadComplete;

    public static boolean clientLoadComplete() {
        return clientLoadComplete;
    }

    public ConnectorMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(EventPriority.HIGHEST, ConnectorMod::onClientSetup);
        FluidHandlerCompat.init(bus);
        if (FMLLoader.getDist().isClient()) {
            bus.addListener(ConnectorMod::onLoadComplete);
        }

        ModList modList = ModList.get();
        if (modList.isLoaded("fabric_object_builder_api_v1")) {
            bus.addListener(EventPriority.HIGHEST, LazyEntityAttributes::initializeLazyAttributes);
        }

        if (ConnectorConfig.usesUnsupportedConfiguration()) {
            ModLoader.get().addWarning(new ModLoadingWarning(
                ModLoadingContext.get().getActiveContainer().getModInfo(),
                ModLoadingStage.CONSTRUCT,
                "Outdated connector_global_mod_aliases.json configuration file detected. Please migrate to the new connector.json configuration."
            ));
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LateRenderTypesInit.regenerateRenderTypeIds();
        clientLoadComplete = true;
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        LateSheetsInit.completeSheetsInit();
    }

    // Injected into mod code by ClassAnalysingTransformer
    @SuppressWarnings("unused")
    public static InputStream getModResourceAsStream(Class<?> clazz, String name) {
        InputStream classRes = clazz.getResourceAsStream(name);
        return classRes != null ? classRes : clazz.getClassLoader().getResourceAsStream(name);
    }
}
