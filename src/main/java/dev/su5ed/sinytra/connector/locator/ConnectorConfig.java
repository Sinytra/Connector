package dev.su5ed.sinytra.connector.locator;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public record ConnectorConfig(int version, List<String> hiddenMods, Multimap<String, String> globalModAliases) {
    public static final Codec<ConnectorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT
            .comapFlatMap(i -> i == 1 ? DataResult.success(i) : DataResult.error(() -> "Unsupported \"version\", must be 1"), Function.identity())
            .optionalFieldOf("version")
            .forGetter(c -> Optional.of(c.version())),
        Codec.STRING
            .listOf()
            .optionalFieldOf("hiddenMods")
            .forGetter(c -> Optional.of(c.hiddenMods())),
        Codec.unboundedMap(
            Codec.STRING,
            Codec.either(Codec.STRING.listOf(), Codec.STRING).xmap(either -> either.map(list -> list, List::of), list -> list.size() == 1 ? Either.right(list.get(0)) : Either.left(list))
        )
            .xmap(map -> {
                Multimap<String, String> aliases = HashMultimap.create();
                map.forEach(aliases::putAll);
                return aliases;
            }, multimap -> {
                Map<String, List<String>> map = new HashMap<>();
                multimap.asMap().forEach((key, val) -> map.put(key, List.copyOf(val)));
                return map;
            })
            .optionalFieldOf("globalModAliases", ImmutableMultimap.of())
            .forGetter(ConnectorConfig::globalModAliases)
    ).apply(instance, ConnectorConfig::new));

    ConnectorConfig(Optional<Integer> version, Optional<List<String>> hiddenMods, Multimap<String, String> globalModAliases) {
        this(version.orElse(1), hiddenMods.orElseGet(List::of), globalModAliases);
    }

    private static final ConnectorConfig DEFAULT = new ConnectorConfig(1, List.of(), ConnectorUtil.DEFAULT_GLOBAL_MOD_ALIASES);
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Supplier<ConnectorConfig> INSTANCE = Suppliers.memoize(() -> {
        Path path = FMLPaths.CONFIGDIR.get().resolve("connector.json");
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    return CODEC.decode(JsonOps.INSTANCE, element).getOrThrow(false, s -> {}).getFirst();
                }
            }
            else {
                JsonElement element = CODEC.encodeStart(JsonOps.INSTANCE, DEFAULT).getOrThrow(false, s -> {});
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                Files.writeString(path, gson.toJson(element));
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading Connector configuration", t);
        }
        return DEFAULT;
    });
}
