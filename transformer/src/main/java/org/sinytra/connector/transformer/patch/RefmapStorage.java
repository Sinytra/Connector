package org.sinytra.connector.transformer.patch;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RefmapStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public record RefmapFiles(SimpleRefmap merged, Map<String, SimpleRefmap> files) {
    }

    public static RefmapFiles processRefmaps(Path input, Collection<String> refmaps) throws IOException {
        SimpleRefmap results = new SimpleRefmap(Map.of(), Map.of());
        Map<String, SimpleRefmap> refmapFiles = new HashMap<>();
        try (FileSystem fs = FileSystems.newFileSystem(input)) {
            for (String refmapFile : refmaps) {
                Path refmapPath = fs.getPath(refmapFile);
                if (Files.notExists(refmapPath)) {
                    LOGGER.warn("Skipping nonexistent refmap {}", refmapFile);
                    continue;
                }

                if (refmapPath != null) {
                    SimpleRefmap refmap = readRefmap(refmapPath);

                    refmapFiles.put(refmapFile, refmap);
                    results = results.merge(refmap);
                } else {
                    LOGGER.warn("Refmap remapper could not find refmap file {}", refmapFile);
                }
            }
        }
        return new RefmapFiles(results, refmapFiles);
    }

    private static SimpleRefmap readRefmap(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, SimpleRefmap.class);
        }
    }
}
