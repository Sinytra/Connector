package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.mod.compat.FluidHandlerCompat;
import dev.su5ed.sinytra.connector.mod.compat.LateSheetsInit;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Locale;

@Mod(ConnectorMod.MODID)
public class ConnectorMod {
    public static final String MODID = "connectormod";

    private static boolean clientLoadComplete;

    public static boolean clientLoadComplete() {
        return clientLoadComplete;
    }

    public ConnectorMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(ConnectorMod::onCommonSetup);
        bus.addListener(ConnectorMod::onClientSetup);
        FluidHandlerCompat.init(bus);
        if (FMLLoader.getDist().isClient()) {
            bus.addListener(ConnectorMod::onLoadComplete);
        }

        ModList modList = ModList.get();
        if (modList.isLoaded("fabric_object_builder_api_v1")) {
            bus.addListener(EventPriority.HIGHEST, LazyEntityAttributes::initializeLazyAttributes);
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        clientLoadComplete = true;
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        LateSheetsInit.completeSheetsInit();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        IModInfo modInfo = ModLoadingContext.get().getActiveContainer().getModInfo();
        URL issueTrackerURL = ((ModFileInfo) modInfo.getOwningFile()).getIssueURL();
        CrashReportCallables.registerCrashCallable("Sinytra Connector", () -> {
            String format = "| %-50.50s | %-30.30s | %-30.30s | %-20.20s |";
            StringBuilder builder = new StringBuilder();
            builder.append(modInfo.getVersion().toString());
            builder.append("\n\t\tSINYTRA CONNECTOR IS PRESENT!");
            builder.append("\n\t\tPlease verify issues are not caused by Connector before reporting them to mod authors. If you're unsure, file a report on Connector's issue tracker.");
            builder.append("\n\t\tConnector's issue tracker can be found at ").append(issueTrackerURL).append(".");
            builder.append("\n\t\tInstalled Fabric mods:");
            builder.append("\n\t\t").append(String.format(Locale.ENGLISH, format,
                StringUtils.repeat('=', 50),
                StringUtils.repeat('=', 30),
                StringUtils.repeat('=', 30),
                StringUtils.repeat('=', 20)
            ));
            ModList.get().getMods().stream()
                .filter(m -> ConnectorEarlyLoader.isConnectorMod(m.getModId()))
                .map(m -> {
                    IModFile mf = m.getOwningFile().getFile();
                    return String.format(Locale.ENGLISH, format,
                        mf.getFileName(),
                        mf.getModInfos().get(0).getDisplayName(),
                        mf.getModInfos().get(0).getModId(),
                        mf.getModInfos().get(0).getVersion());
                })
                .forEach(s -> builder.append("\n\t\t").append(s));
            builder.append("\n");
            return builder.toString();
        });
    }
}
