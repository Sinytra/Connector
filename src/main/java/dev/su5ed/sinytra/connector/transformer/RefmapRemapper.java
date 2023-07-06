package dev.su5ed.sinytra.connector.transformer;

import com.google.gson.Gson;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.fart.api.Transformer;

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
import java.util.jar.Manifest;

public class RefmapRemapper implements Transformer {
    private static final Gson GSON = new Gson();
    private static final String INTERMEDIARY_MAPPING_ENV = "named:intermediary";
    private static final String SRG_MAPPING_ENV = "searge";

    private final Collection<String> configs;
    private final Collection<String> refmaps;
    private final SrgRemappingReferenceMapper remapper;

    public RefmapRemapper(Collection<String> configs, Collection<String> refmaps, SrgRemappingReferenceMapper remapper) {
        this.configs = configs;
        this.refmaps = refmaps;
        this.remapper = remapper;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (this.refmaps.contains(entry.getName())) {
            byte[] data = remapRefmapInPlace(entry.getData());
            return ResourceEntry.create(entry.getName(), entry.getTime(), data);
        }
        return entry;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        Manifest manifest = new Manifest();
        try (InputStream is = new ByteArrayInputStream(entry.getData())) {
            manifest.read(is);

            manifest.getMainAttributes().putValue(ConnectorUtil.MIXIN_CONFIGS_ATTRIBUTE, String.join(",", this.configs));

            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                manifest.write(byteStream);
                return ManifestEntry.create(entry.getTime(), byteStream.toByteArray());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
