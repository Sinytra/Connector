package dev.su5ed.sinytra.connector.transformer;

import com.google.gson.Gson;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.fart.api.Transformer;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class RefmapRemapper implements Transformer {
    private static final Gson GSON = new Gson();
    private static final String INTERMEDIARY_MAPPING_ENV = "named:intermediary";
    private static final String SRG_MAPPING_ENV = "searge";

    private final Map<String, String> configs;
    private final Collection<String> refmaps;
    private final SrgRemappingReferenceMapper remapper;

    public RefmapRemapper(Collection<String> configs, Collection<String> refmaps, SrgRemappingReferenceMapper remapper) {
        this.configs = configs.stream()
            // Some mods (specifically mixinextras) are present on both platforms, and mixin can fail to select the correct configs for
            // each jar due to their names being the same. To avoid conflicts, we assign fabric mixin configs new, unique names.
            .collect(Collectors.toMap(Function.identity(), name -> {
                // Split file name and extension
                String[] parts = name.split("\\.(?!.*\\.)");
                // Append unique string to file name
                return parts[0] + "-" + RandomStringUtils.randomAlphabetic(5) + "." + parts[1];
            }));
        this.refmaps = refmaps;
        this.remapper = remapper;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        String name = entry.getName();
        if (this.refmaps.contains(name)) {
            byte[] data = remapRefmapInPlace(entry.getData());
            return ResourceEntry.create(name, entry.getTime(), data);
        }
        String rename = this.configs.get(name);
        if (rename != null) {
            return ResourceEntry.create(rename, entry.getTime(), entry.getData());
        }
        return entry;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        if (!this.configs.isEmpty()) {
            Manifest manifest = new Manifest();
            try (InputStream is = new ByteArrayInputStream(entry.getData())) {
                manifest.read(is);

                manifest.getMainAttributes().putValue(ConnectorUtil.MIXIN_CONFIGS_ATTRIBUTE, String.join(",", this.configs.values()));

                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                    manifest.write(byteStream);
                    return ManifestEntry.create(entry.getTime(), byteStream.toByteArray());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return entry;
    }

    private byte[] remapRefmapInPlace(byte[] data) {
        try {
            Reader reader = new InputStreamReader(new ByteArrayInputStream(data));
            SrgRemappingReferenceMapper.SimpleRefmap simpleRefmap = GSON.fromJson(reader, SrgRemappingReferenceMapper.SimpleRefmap.class);
            Map<String, String> replacements = Map.of(INTERMEDIARY_MAPPING_ENV, SRG_MAPPING_ENV);
            SrgRemappingReferenceMapper.SimpleRefmap remapped = this.remapper.remap(simpleRefmap, replacements);

            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                try (Writer writer = new OutputStreamWriter(byteStream)) {
                    remapped.write(writer);
                    writer.flush();
                }
                return byteStream.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
