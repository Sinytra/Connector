package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.util.LoaderUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public class GlobalModAliases {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Multimap<String, String> aliases;

    public GlobalModAliases(Path configDir, Multimap<String, String> defaultValues) {
        Path path = configDir.resolve("connector_global_mod_aliases.json");

        if (!Files.exists(path)) {
            this.aliases = ImmutableMultimap.copyOf(defaultValues);
            write(path);
            return;
        }

        try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            this.aliases = parse(reader);
        } catch (IOException | ParseMetadataException e) {
            throw FormattedException.ofLocalized("exception.parsingOverride", "Failed to parse " + LoaderUtil.normalizePath(path), e);
        }
    }

    public Multimap<String, String> getAliases() {
        return this.aliases;
    }

    private Multimap<String, String> parse(JsonReader reader) throws IOException, ParseMetadataException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new ParseMetadataException("Root must be an object", reader);
        }
        
        reader.beginObject();

        if (!reader.nextName().equals("version")) {
            throw new ParseMetadataException("First key must be \"version\"", reader);
        }

        if (reader.peek() != JsonToken.NUMBER || reader.nextInt() != 1) {
            throw new ParseMetadataException("Unsupported \"version\", must be 1", reader);
        }

        ImmutableMultimap.Builder<String, String> aliases = ImmutableMultimap.builder();

        while (reader.hasNext()) {
            String key = reader.nextName();

            if ("aliases".equals(key)) {
                reader.beginObject();

                while (reader.hasNext()) {
                    String modid = reader.nextName();

                    switch (reader.peek()) {
                        case STRING -> {
                            String alias = reader.nextString();
                            aliases.put(modid, alias);
                        }
                        case BEGIN_ARRAY -> {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                String alias = reader.nextString();
                                aliases.put(modid, alias);
                            }
                            reader.endArray();
                        }
                        default -> throw new ParseMetadataException("Mod aliases must be a string or string array!", reader);
                    }
                }

                reader.endObject();
            }
            else {
                throw new ParseMetadataException("Unsupported root key: " + key, reader);
            }
        }

        reader.endObject();

        return aliases.build();
    }

    private void write(Path path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try {
            Files.createDirectories(path.getParent());

            try (JsonWriter writer = gson.newJsonWriter(Files.newBufferedWriter(path))) {
                writer.beginObject();

                writer.name("version").value(1);

                writer.name("aliases").beginObject();
                for (Map.Entry<String, Collection<String>> entry : this.aliases.asMap().entrySet()) {
                    Collection<String> values = entry.getValue();
                    writer.name(entry.getKey());
                    if (values.size() == 1) {
                        writer.value(values.iterator().next());
                    }
                    else {
                        writer.beginArray();
                        for (String value : values) {
                            writer.value(value);
                        }
                        writer.endArray();
                    }
                }
                writer.endObject();

                writer.endObject();
            }
        } catch (IOException e) {
            LOGGER.error("Error writing default global mod aliases", e);
        }
    }
}
