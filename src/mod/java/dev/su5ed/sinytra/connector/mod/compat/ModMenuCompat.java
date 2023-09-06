package dev.su5ed.sinytra.connector.mod.compat;

import com.mojang.logging.LogUtils;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModMenuCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODID = "modmenu";

    public static void init(FMLClientSetupEvent event) {
        Map<String, ConfigScreenFactory<?>> modFactories = new HashMap<>();
        List<Map<String, ConfigScreenFactory<?>>> providedFactories = new ArrayList<>();
        FabricLoader.getInstance().getEntrypointContainers(MODID, ModMenuApi.class).forEach(container -> {
            String modId = container.getProvider().getMetadata().getId();
            try {
                ModMenuApi entry = container.getEntrypoint();
                modFactories.put(modId, entry.getModConfigScreenFactory());
                providedFactories.add(entry.getProvidedConfigScreenFactories());
            } catch (Throwable t) {
                LOGGER.error("Failed to load ModMenuApi entrypoint for {}", modId, t);
            }
        });

        providedFactories.forEach(map -> map.forEach(modFactories::putIfAbsent));
        providedFactories.clear();

        Screen dummyParent = new ModListScreen(null);
        modFactories.forEach((modId, factory) -> {
            // Ensure factory is active. This is required to avoid cases where the integration conditionally
            // disables itself (e.g. when cloth config is absent) and returns a dummy factory.
            try {
                if (factory.create(dummyParent) == null) {
                    return;
                }
            } catch (Throwable t) {
                // If an error occurs, it might be due to us creating the factory so early.
                // Since we can't be sure about the factory's status, continue ahead
                LOGGER.warn("Error testing config screen factory status for mod {}", modId, t);
            }

            ModList.get().getModContainerById(modId).ifPresent(fmlContainer ->
                fmlContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> factory.create(screen))));
        });
    }
}
