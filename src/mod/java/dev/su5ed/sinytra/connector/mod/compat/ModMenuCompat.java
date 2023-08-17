package dev.su5ed.sinytra.connector.mod.compat;

import com.mojang.logging.LogUtils;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModMenuCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODID = "modmenu";

    public static void init() {
        Map<String, ConfigScreenFactory<?>> modFactories = new HashMap<>();
        List<Map<String, ConfigScreenFactory<?>>> providedFactories = new ArrayList<>();
        FabricLoader.getInstance().getEntrypointContainers(MODID, ModMenuApi.class).forEach(container -> {
            String modId = container.getProvider().getMetadata().getId();
            try {
                ModMenuApi entry = container.getEntrypoint();
                modFactories.put(modId, entry.getModConfigScreenFactory());
                providedFactories.add(entry.getProvidedConfigScreenFactories());
            } catch (Exception e) {
                LOGGER.error("Failed to load ModMenuApi entrypoint for {}", modId, e);
            }
        });

        providedFactories.forEach(map -> map.forEach(modFactories::putIfAbsent));
        providedFactories.clear();

        modFactories.forEach((modId, factory) ->
            ModList.get().getModContainerById(modId).ifPresent(fmlContainer ->
                fmlContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> factory.create(screen)))));
    }
}
