package dev.su5ed.sinytra.connector.mod;

import com.electronwill.nightconfig.core.file.FileConfigBuilder;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import com.google.gson.JsonElement;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.locator.ConnectorConfig;
import dev.su5ed.sinytra.connector.mod.compat.DummyResourceManager;
import dev.su5ed.sinytra.connector.mod.compat.FluidHandlerCompat;
import dev.su5ed.sinytra.connector.mod.compat.LateRenderTypesInit;
import dev.su5ed.sinytra.connector.mod.compat.LateSheetsInit;
import dev.su5ed.sinytra.connector.mod.compat.LazyEntityAttributes;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

@Mod(ConnectorUtil.CONNECTOR_MODID)
public class ConnectorMod {
    public static final Logger LOG = LoggerFactory.getLogger(ConnectorMod.class);

    private static boolean clientLoadComplete;
    private static boolean preventFreeze;

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

    // Injected into mod code by ClassAnalysingTransformer
    @SuppressWarnings("unused")
    public static <T> Optional<T> deserializeLootTable(LootDataType<T> type, ResourceLocation location, JsonElement json) {
        return type.deserialize(location, json, DummyResourceManager.INSTANCE);
    }

    // Injected into mod code by ClassAnalysingTransformer
    @SuppressWarnings("unused")
    public static GenericBuilder<?, ?> useModConfigResource(FileConfigBuilder builder, String resource) {
        URL url = ConnectorMod.class.getClassLoader().getResource(resource);
        return builder.onFileNotFound(FileNotFoundAction.copyData(url));
    } 

    @SuppressWarnings("deprecation")
    public static void unfreezeRegistries() {
        ((MappedRegistry<?>) BuiltInRegistries.REGISTRY).unfreeze();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ((MappedRegistry<?>) registry).unfreeze();
        }
        preventFreeze = true;
    }

    public static boolean preventFreeze() {
        return preventFreeze;
    }
}
