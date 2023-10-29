package dev.su5ed.sinytra.connector.locator;

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public record ConnectorConfig(List<String> hiddenMods) {
    private static final ConnectorConfig DEFAULT = new ConnectorConfig(List.of());
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Supplier<ConnectorConfig> INSTANCE = Suppliers.memoize(() -> {
        Path path = FMLPaths.CONFIGDIR.get().resolve("connector.json");
        try {
            if (Files.exists(path)) {
                Gson gson = new Gson();
                try (Reader reader = Files.newBufferedReader(path)) {
                    return gson.fromJson(reader, ConnectorConfig.class);
                }
            }
            else {
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                Files.writeString(path, gson.toJson(DEFAULT));
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading Connector configuration", t);
        }
        return DEFAULT;
    });
}
