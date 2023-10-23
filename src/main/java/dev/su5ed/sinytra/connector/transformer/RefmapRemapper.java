package dev.su5ed.sinytra.connector.transformer;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.fart.api.Transformer;
import org.slf4j.Logger;
import org.spongepowered.asm.util.Constants;

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
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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

    private final Collection<String> visibleMixinConfigs;
    private final Map<String, SrgRemappingReferenceMapper.SimpleRefmap> files;

    private boolean hasManifest;

    public RefmapRemapper(Collection<String> visibleMixinConfigs, Map<String, SrgRemappingReferenceMapper.SimpleRefmap> files) {
        this.visibleMixinConfigs = visibleMixinConfigs;
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
        return entry;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        this.hasManifest = true;
        if (!this.visibleMixinConfigs.isEmpty()) {
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
        if (!this.visibleMixinConfigs.isEmpty() && !this.hasManifest) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            return List.of(modifyManifest(manifest, ConnectorUtil.ZIP_TIME));
        }
        return List.of();
    }

    private ManifestEntry modifyManifest(Manifest manifest, long time) {
        manifest.getMainAttributes().putValue(Constants.ManifestAttributes.MIXINCONFIGS, String.join(",", this.visibleMixinConfigs));
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
