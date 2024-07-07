package org.sinytra.connector.transformer;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import org.sinytra.connector.util.ConnectorUtil;
import net.minecraftforge.fart.api.Transformer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.util.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private static final String MOJ_MAPPING_ENV = "mojang";

    public record RefmapFiles(MappingAwareReferenceMapper.SimpleRefmap merged, Map<String, MappingAwareReferenceMapper.SimpleRefmap> files) {}

    public static RefmapFiles processRefmaps(Path input, Collection<String> refmaps, MappingAwareReferenceMapper remapper, List<Path> libs) throws IOException {
        MappingAwareReferenceMapper.SimpleRefmap results = new MappingAwareReferenceMapper.SimpleRefmap(Map.of(), Map.of());
        Map<String, MappingAwareReferenceMapper.SimpleRefmap> refmapFiles = new HashMap<>();
        try (FileSystem fs = FileSystems.newFileSystem(input)) {
            for (String refmap : refmaps) {
                Path refmapPath = fs.getPath(refmap);
                if (Files.notExists(refmapPath)) {
                    refmapPath = findRefmapOnClasspath(refmap, input, libs);
                }
                if (refmapPath != null) {
                    byte[] data = Files.readAllBytes(refmapPath);
                    MappingAwareReferenceMapper.SimpleRefmap remapped = remapRefmapInPlace(data, remapper);
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

    @Nullable
    private static Path findRefmapOnClasspath(String resource, Path exclude, List<Path> libs) {
        for (Path lib : libs) {
            if (lib == exclude) {
                continue;
            }
            Path basePath;
            if (Files.isDirectory(lib)) {
                basePath = lib;
            } else {
                try {
                    FileSystem fs = FileSystems.newFileSystem(lib);
                    basePath = fs.getPath("");
                } catch (Exception e) {
                    LOGGER.error("Error opening jar file", e);
                    return null;
                }
            }
            Path path = basePath.resolve(resource);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private final Collection<String> visibleMixinConfigs;
    private final Map<String, MappingAwareReferenceMapper.SimpleRefmap> files;

    private boolean hasManifest;

    public RefmapRemapper(Collection<String> visibleMixinConfigs, Map<String, MappingAwareReferenceMapper.SimpleRefmap> files) {
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

    private static MappingAwareReferenceMapper.SimpleRefmap remapRefmapInPlace(byte[] data, MappingAwareReferenceMapper remapper) {
        Reader reader = new InputStreamReader(new ByteArrayInputStream(data));
        MappingAwareReferenceMapper.SimpleRefmap simpleRefmap = GSON.fromJson(reader, MappingAwareReferenceMapper.SimpleRefmap.class);
        Map<String, String> replacements = Map.of(INTERMEDIARY_MAPPING_ENV, MOJ_MAPPING_ENV);
        return remapper.remap(simpleRefmap, replacements);
    }
}
