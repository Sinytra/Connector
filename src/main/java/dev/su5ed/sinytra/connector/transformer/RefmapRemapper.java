package dev.su5ed.sinytra.connector.transformer;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.fart.api.Transformer;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class RefmapRemapper implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String INTERMEDIARY_MAPPING_ENV = "named:intermediary";
    private static final String SRG_MAPPING_ENV = "searge";

    public record RefmapFiles(SrgRemappingReferenceMapper.SimpleRefmap merged, Map<String, SrgRemappingReferenceMapper.SimpleRefmap> files) {}

    public static RefmapFiles processRefmaps(File input, Collection<String> refmaps, SrgRemappingReferenceMapper remapper) throws IOException {
        SrgRemappingReferenceMapper.SimpleRefmap results = new SrgRemappingReferenceMapper.SimpleRefmap(Map.of(), Map.of());
        Map<String, SrgRemappingReferenceMapper.SimpleRefmap> refmapFiles = new HashMap<>();
        try (FileSystem fs = FileSystems.newFileSystem(input.toPath())) {
            for (String refmap : refmaps) {
                Path refmapPath = fs.getPath(refmap);
                if (Files.exists(refmapPath)) {
                    byte[] data = Files.readAllBytes(refmapPath);
                    SrgRemappingReferenceMapper.SimpleRefmap remapped = remapRefmapInPlace(data, remapper);
                    refmapFiles.put(refmap, remapped);
                    results = results.merge(remapped);
                }
                else {
                    LOGGER.warn("Refmap remapper could not find refmap file {}", refmap);
                }
            }
        }
        return new RefmapFiles(results, refmapFiles);
    }

    private final Map<String, String> configs;
    private final Map<String, SrgRemappingReferenceMapper.SimpleRefmap> files;

    private boolean hasManifest;

    public RefmapRemapper(Collection<String> configs, Map<String, SrgRemappingReferenceMapper.SimpleRefmap> files, boolean makeUniqueConfigNames) {
        this.configs = configs.stream()
            // Some mods (specifically mixinextras) are present on both platforms, and mixin can fail to select the correct configs for
            // each jar due to their names being the same. To avoid conflicts, we assign fabric mixin configs new, unique names.
            .collect(Collectors.toMap(Function.identity(), name -> {
                if (makeUniqueConfigNames) {
                    // Split file name and extension
                    String[] parts = name.split("\\.(?!.*\\.)");
                    // Append unique string to file name
                    return parts[0] + "-" + RandomStringUtils.randomAlphabetic(5) + "." + parts[1];
                }
                return name;
            }));
        this.files = files;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        String name = entry.getName();
        if (this.files.containsKey(name)) {
            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                try (Writer writer = new OutputStreamWriter(byteStream)) {
                    this.files.get(name).write(writer);
                    writer.flush();
                }
                byte[] data = byteStream.toByteArray();
                return ResourceEntry.create(name, entry.getTime(), data);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        String rename = this.configs.get(name);
        if (rename != null) {
            return ResourceEntry.create(rename, entry.getTime(), entry.getData());
        }
        return entry;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        this.hasManifest = true;
        if (!this.configs.isEmpty()) {
            Manifest manifest = new Manifest();
            try (InputStream is = new ByteArrayInputStream(entry.getData())) {
                manifest.read(is);
                return modifyManifest(manifest, entry.getTime());
            } catch (IOException e) {
                throw new UncheckedIOException("Error writing manifest", e);
            }
        }
        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        if (!this.configs.isEmpty() && !this.hasManifest) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            return List.of(modifyManifest(manifest, ConnectorUtil.ZIP_TIME));
        }
        return List.of();
    }

    private ManifestEntry modifyManifest(Manifest manifest, long time) {
        manifest.getMainAttributes().putValue(ConnectorUtil.MIXIN_CONFIGS_ATTRIBUTE, String.join(",", this.configs.values()));
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            manifest.write(byteStream);
            return ManifestEntry.create(time, byteStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing generated manifest", e);
        }
    }

    private static SrgRemappingReferenceMapper.SimpleRefmap remapRefmapInPlace(byte[] data, SrgRemappingReferenceMapper remapper) {
        Reader reader = new InputStreamReader(new ByteArrayInputStream(data));
        SrgRemappingReferenceMapper.SimpleRefmap simpleRefmap = GSON.fromJson(reader, SrgRemappingReferenceMapper.SimpleRefmap.class);
        Map<String, String> replacements = Map.of(INTERMEDIARY_MAPPING_ENV, SRG_MAPPING_ENV);
        return remapper.remap(simpleRefmap, replacements);
    }
}
